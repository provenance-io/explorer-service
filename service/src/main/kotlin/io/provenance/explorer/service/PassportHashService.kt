package io.provenance.explorer.service

import com.github.benmanes.caffeine.cache.Caffeine
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.config.pulse.PulseProperties
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
    private val passportAccountsCache =
        Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(10)
            .build<String, Set<String>>()

    /**
     * Sums nhash holdings (bank + delegated + rewards) across all passport accounts.
     */
    fun sumHashHoldings(atDateTime: LocalDateTime? = null): BigDecimal {
        val accounts = getPassportAccounts()
        if (accounts.isEmpty()) {
            return BigDecimal.ZERO
        }

        val height = atDateTime?.let { BlockCacheRecord.getLastBlockBeforeTime(it) }

        return runBlocking {
            accounts.map { address ->
                async {
                    semaphore.withPermit {
                        sumHashForAccount(address, height)
                    }
                }
            }.awaitAll().sumOf { it }
        }
    }

    private fun getPassportAccounts(): Set<String> =
        passportAccountsCache.get(pulseProperties.passportAttributeName) { attributeName ->
            runBlocking {
                attributeGrpcClient.getAccountsForAttribute(attributeName)
            }
        } ?: emptySet()

    private suspend fun sumHashForAccount(address: String, height: Int?): BigDecimal {
        val bankBalance = if (height != null) {
            accountGrpcClient.getAccountBalanceForDenomAtHeight(address, UTILITY_TOKEN, height)
                .amount.toBigDecimal()
        } else {
            accountGrpcClient.getAccountBalanceForDenom(address, UTILITY_TOKEN)
                .amount.toBigDecimal()
        }

        return bankBalance
            .add(delegationTotalNhash(address, height))
            .add(rewardsTotalNhash(address, height))
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
