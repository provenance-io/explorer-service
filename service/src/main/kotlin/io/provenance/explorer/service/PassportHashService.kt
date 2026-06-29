package io.provenance.explorer.service

import com.github.benmanes.caffeine.cache.Caffeine
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.config.pulse.PulseProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.extensions.toDecimalStringOld
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provenance.explorer.grpc.v1.AttributeGrpcClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Service
class PassportHashService(
    private val attributeGrpcClient: AttributeGrpcClient,
    private val accountGrpcClient: AccountGrpcClient,
    private val pulseProperties: PulseProperties,
    private val semaphore: Semaphore,
) {
    protected val logger = logger(PassportHashService::class)

    private val passportAccountsCache =
        Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(10)
            .build<String, Set<String>>()

    /**
     * Returns passport account addresses, cached for 24 hours.
     */
    fun getPassportAccounts(): Set<String> =
        passportAccountsCache.get(pulseProperties.passportAttributeName) { attributeName ->
            runBlocking {
                val accounts = attributeGrpcClient.getAccountsForAttribute(attributeName)
                logger.info(
                    "Fetched ${accounts.size} passport accounts for attribute $attributeName, " +
                        "sample=${accounts.take(3)}"
                )
                accounts
            }
        } ?: emptySet()

    /**
     * Sums nhash holdings (bank + delegated + rewards) across all passport accounts.
     */
    fun sumHashHoldings(accounts: Set<String>, atDateTime: LocalDateTime? = null): BigDecimal {
        if (accounts.isEmpty()) {
            logger.info("Passport HASH sum: no accounts, total nhash=0")
            return BigDecimal.ZERO
        }

        val height = atDateTime?.let { BlockCacheRecord.getLastBlockBeforeTime(it) }

        return runBlocking {
            val perAccount = accounts.map { address ->
                async {
                    semaphore.withPermit {
                        sumHashForAccount(address, height)
                    }
                }
            }.awaitAll()

            val totalNhash = perAccount.sumOf { it.totalNhash }
            val totalBank = perAccount.sumOf { it.bankBalance }
            val totalDelegated = perAccount.sumOf { it.delegatedBalance }
            val totalRewards = perAccount.sumOf { it.rewardsBalance }
            val nonZeroAccounts = perAccount.count { it.totalNhash > BigDecimal.ZERO }

            logger.info(
                "Passport HASH sum: ${accounts.size} accounts ($nonZeroAccounts non-zero), " +
                    "total nhash=$totalNhash (bank=$totalBank, delegated=$totalDelegated, rewards=$totalRewards), " +
                    "height=$height, atDateTime=$atDateTime"
            )

            perAccount
                .sortedByDescending { it.totalNhash }
                .take(10)
                .forEach {
                    logger.info(
                        "Passport top holder ${it.address}: bank=${it.bankBalance}, " +
                            "delegated=${it.delegatedBalance}, rewards=${it.rewardsBalance}, " +
                            "total nhash=${it.totalNhash}"
                    )
                }

            totalNhash
        }
    }

    private data class AccountHashHoldings(
        val address: String,
        val bankBalance: BigDecimal,
        val delegatedBalance: BigDecimal,
        val rewardsBalance: BigDecimal,
    ) {
        val totalNhash: BigDecimal = bankBalance.add(delegatedBalance).add(rewardsBalance)
    }

    private suspend fun sumHashForAccount(address: String, height: Int?): AccountHashHoldings {
        val bankBalance = if (height != null) {
            accountGrpcClient.getAccountBalanceForDenomAtHeight(address, UTILITY_TOKEN, height)
                .amount.toBigDecimal()
        } else {
            accountGrpcClient.getAccountBalanceForDenom(address, UTILITY_TOKEN)
                .amount.toBigDecimal()
        }

        val delegatedBalance = delegationTotalNhash(address, height)
        val rewardsBalance = rewardsTotalNhash(address, height)

        return AccountHashHoldings(address, bankBalance, delegatedBalance, rewardsBalance)
    }

    private suspend fun delegationTotalNhash(address: String, height: Int?): BigDecimal {
        var offset = 0
        val limit = 100

        val results = if (height != null) {
            accountGrpcClient.getDelegationsAtHeight(address, offset, limit, height)
        } else {
            accountGrpcClient.getDelegations(address, offset, limit)
        }

        val total = results.pagination?.total ?: results.delegationResponsesCount.toLong()
        val delegations = results.delegationResponsesList.toMutableList()

        while (delegations.size < total) {
            offset += limit
            val page = if (height != null) {
                accountGrpcClient.getDelegationsAtHeight(address, offset, limit, height)
            } else {
                accountGrpcClient.getDelegations(address, offset, limit)
            }
            delegations.addAll(page.delegationResponsesList)
        }

        return delegations
            .filter { it.balance.denom == UTILITY_TOKEN }
            .sumOf { it.balance.amount.toBigDecimal() }
    }

    private suspend fun rewardsTotalNhash(address: String, height: Int?): BigDecimal {
        val rewards = if (height != null) {
            accountGrpcClient.getRewardsAtHeight(address, height)
        } else {
            accountGrpcClient.getRewards(address)
        }

        return rewards.totalList
            .filter { it.denom == UTILITY_TOKEN }
            .sumOf { it.amount.toDecimalStringOld().toBigDecimal() }
    }
}
