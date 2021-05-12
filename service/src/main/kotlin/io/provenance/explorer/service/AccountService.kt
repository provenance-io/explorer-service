package io.provenance.explorer.service

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.AccountRecord.Companion.update
import io.provenance.explorer.domain.extensions.NHASH
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toDecCoin
import io.provenance.explorer.domain.extensions.toHash
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.toSigObj
import io.provenance.explorer.domain.models.explorer.AccountDetail
import io.provenance.explorer.domain.models.explorer.AccountRewards
import io.provenance.explorer.domain.models.explorer.CoinStr
import io.provenance.explorer.domain.models.explorer.Delegation
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.Reward
import io.provenance.explorer.domain.models.explorer.toData
import io.provenance.explorer.grpc.extensions.getModuleAccName
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class AccountService(
    private val accountClient: AccountGrpcClient,
    private val props: ExplorerProperties
) {

    protected val logger = logger(AccountService::class)

    fun getAccountRaw(address: String) = transaction { AccountRecord.findByAddress(address) } ?: saveAccount(address)

    fun saveAccount(address: String) =
        accountClient.getAccountInfo(address)?.let { AccountRecord.insertIgnore(it) }
            ?: AccountRecord.insertUnknownAccount(address)

    fun getAccountDetail(address: String) = getAccountRaw(address).let {
        AccountDetail(
            it.type,
            it.accountAddress,
            it.accountNumber,
            it.baseAccount?.sequence?.toInt(),
            AccountRecord.findSigsByAddress(it.accountAddress).toSigObj(props.provAccPrefix()),
            getAccountBalances(address),
            it.data?.getModuleAccName()
        )
    }

    fun getAccountBalances(address: String) = accountClient.getAccountBalances(address).map { it.toData()}

    fun getCurrentSupply(denom: String) = accountClient.getCurrentSupply(denom).amount

    fun getDelegations(address: String, page: Int, limit: Int) =
        accountClient.getDelegations(address, page.toOffset(limit), limit).let { res ->
            val list = res.delegationResponsesList.map {
                Delegation(
                    it.delegation.delegatorAddress,
                    it.delegation.validatorAddress,
                    null,
                    it.balance.amount.toHash(it.balance.denom).let { coin -> CoinStr(coin.first, coin.second, it.balance.denom) },
                    null,
                    it.delegation.shares.toDecCoin(),
                    null,
                    null)
            }
            PagedResults(res.pagination.total.pageCountOfResults(limit), list)
        }

    fun getUnbondingDelegations(address: String) =
        accountClient.getUnbondingDelegations(address, 0, 100).let { res ->
            res.unbondingResponsesList.flatMap { list ->
                list.entriesList.map {
                    Delegation(
                        list.delegatorAddress,
                        list.validatorAddress,
                        null,
                        it.balance.toHash(NHASH).let { coin -> CoinStr(coin.first, coin.second, NHASH) },
                        it.initialBalance.toHash(NHASH).let { coin -> CoinStr(coin.first, coin.second, NHASH) },
                        null,
                        it.creationHeight.toInt(),
                        it.completionTime.toDateTime()
                    )
                }
            }
        }

    fun getRedelegations(address: String) =
        accountClient.getRedelegations(address, 0, 100).let { res ->
            res.redelegationResponsesList.flatMap { list ->
                list.entriesList.map {
                    Delegation(
                        list.redelegation.delegatorAddress,
                        list.redelegation.validatorSrcAddress,
                        list.redelegation.validatorDstAddress,
                        it.balance.toHash(NHASH).let { coin -> CoinStr(coin.first, coin.second, NHASH) },
                        it.redelegationEntry.initialBalance.toHash(NHASH).let { coin -> CoinStr(coin.first, coin.second, NHASH) },
                        it.redelegationEntry.sharesDst.toDecCoin(),
                        it.redelegationEntry.creationHeight.toInt(),
                        it.redelegationEntry.completionTime.toDateTime()
                    )
                }
            }
        }

    fun getRewards(address: String) = accountClient.getRewards(address).let { res ->
        AccountRewards(
            res.rewardsList.map { list ->
                Reward(
                    list.validatorAddress,
                    list.rewardList.map { r -> CoinStr(r.amount.toDecCoin(), r.denom, r.denom) })
            },
            res.totalList.map { t -> CoinStr(t.amount.toDecCoin(), t.denom, t.denom) }
        )
    }

    fun updateAccounts(accs: Set<Int>) = transaction {
        logger.info("Updating accounts")
        accs.forEach { id ->
            val record = AccountRecord.findById(id)!!
            val data = accountClient.getAccountInfo(record.accountAddress)
            if (data != null && data != record.data)
                record.update(data)
        }
    }
}

fun String.getAccountType() = this.split(".").last()
