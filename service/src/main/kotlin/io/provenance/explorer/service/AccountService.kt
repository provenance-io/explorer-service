package io.provenance.explorer.service

import io.provenance.attribute.v1.Attribute
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.extensions.NHASH
import io.provenance.explorer.domain.extensions.fromBase64
import io.provenance.explorer.domain.extensions.isAddressAsType
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toAccountPubKey
import io.provenance.explorer.domain.extensions.toBase64
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toDecCoin
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.AccountDetail
import io.provenance.explorer.domain.models.explorer.AccountRewards
import io.provenance.explorer.domain.models.explorer.AttributeObj
import io.provenance.explorer.domain.models.explorer.CoinStr
import io.provenance.explorer.domain.models.explorer.Delegation
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.Reward
import io.provenance.explorer.domain.models.explorer.TokenCounts
import io.provenance.explorer.domain.models.explorer.UnpaginatedDelegation
import io.provenance.explorer.domain.models.explorer.toCoinStrWithPrice
import io.provenance.explorer.grpc.extensions.getModuleAccName
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provenance.explorer.grpc.v1.AttributeGrpcClient
import io.provenance.explorer.grpc.v1.MetadataGrpcClient
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class AccountService(
    private val accountClient: AccountGrpcClient,
    private val props: ExplorerProperties,
    private val attrClient: AttributeGrpcClient,
    private val metadataClient: MetadataGrpcClient,
    private val assetService: AssetService
) {

    protected val logger = logger(AccountService::class)

    fun getAccountRaw(address: String) = transaction { AccountRecord.findByAddress(address) } ?: saveAccount(address)

    fun saveAccount(address: String, isContract: Boolean = false) = runBlocking {
        accountClient.getAccountInfo(address)?.let { AccountRecord.insertIgnore(it, isContract) }
            ?: if (address.isAddressAsType(props.provAccPrefix())) AccountRecord.insertUnknownAccount(
                address,
                isContract
            )
            else throw ResourceNotFoundException("Invalid account: '$address'")
    }

    fun getAccountDetail(address: String) = runBlocking {
        getAccountRaw(address).let {
            val attributes = async { attrClient.getAllAttributesForAddress(it.accountAddress) }
            val tokenCount = async { getBalances(it.accountAddress, 0, 1) }
            val balances = async { getBalances(it.accountAddress, 1, tokenCount.await().pagination.total.toInt()) }
            AccountDetail(
                it.type,
                it.accountAddress,
                it.accountNumber,
                it.baseAccount?.sequence?.toInt(),
                AccountRecord.findSigsByAddress(it.accountAddress).firstOrNull().toAccountPubKey(),
                it.data?.getModuleAccName(),
                attributes.await().map { attr -> attr.toResponse() },
                TokenCounts(
                    tokenCount.await().pagination.total,
                    metadataClient.getScopesByOwner(it.accountAddress).pagination.total.toInt()
                ),
                it.isContract,
                assetService.getAumForList(balances.await().balancesList.associate { bal -> bal.denom to bal.amount })
                    .toCoinStr("USD")
            )
        }
    }

    fun getNamesOwnedByAccount(address: String, page: Int, limit: Int) = runBlocking {
        attrClient.getNamesForAddress(address, page.toOffset(limit), limit).let { res ->
            val names = res.nameList.toList()
            PagedResults(res.pagination.total.pageCountOfResults(limit), names, res.pagination.total)
        }
    }

    suspend fun getBalances(address: String, page: Int, limit: Int) =
        accountClient.getAccountBalances(address, page.toOffset(limit), limit)

    fun getAccountBalances(address: String, page: Int, limit: Int) = runBlocking {
        getBalances(address, page, limit).let { res ->
            val pricing = assetService.getPricingInfoIn(res.balancesList.map { it.denom })
            val bals = res.balancesList.map { it.toCoinStrWithPrice(pricing[it.denom]) }
            PagedResults(res.pagination.total.pageCountOfResults(limit), bals, res.pagination.total)
        }
    }

    private fun getDelegationTotal(address: String) = runBlocking {
        var offset = 0
        val limit = 100

        val results = accountClient.getDelegations(address, offset, limit)
        val total = results.pagination?.total ?: results.delegationResponsesCount.toLong()
        val delegations = results.delegationResponsesList.toMutableList()

        while (delegations.count() < total) {
            offset += limit
            accountClient.getDelegations(address, offset, limit).let { delegations.addAll(it.delegationResponsesList) }
        }
        delegations.sumOf { it.balance.amount.toBigDecimal() }
            .toCoinStr(delegations.firstOrNull()?.balance?.denom ?: NHASH)
    }

    fun getDelegations(address: String, page: Int, limit: Int) = runBlocking {
        accountClient.getDelegations(address, page.toOffset(limit), limit).let { res ->
            val list = res.delegationResponsesList.map {
                Delegation(
                    it.delegation.delegatorAddress,
                    it.delegation.validatorAddress,
                    null,
                    CoinStr(it.balance.amount, it.balance.denom),
                    null,
                    it.delegation.shares.toDecCoin(),
                    null,
                    null
                )
            }
            val rollup = mapOf("bondedTotal" to getDelegationTotal(address))
            PagedResults(res.pagination.total.pageCountOfResults(limit), list, res.pagination.total, rollup)
        }
    }

    fun getUnbondingDelegations(address: String) = runBlocking {
        accountClient.getUnbondingDelegations(address, 0, 100).let { res ->
            res.unbondingResponsesList.flatMap { list ->
                list.entriesList.map {
                    Delegation(
                        list.delegatorAddress,
                        list.validatorAddress,
                        null,
                        CoinStr(it.balance, NHASH),
                        CoinStr(it.initialBalance, NHASH),
                        null,
                        it.creationHeight.toInt(),
                        it.completionTime.toDateTime()
                    )
                }
            }
        }.let { recs ->
            val total = recs.sumOf { it.amount.amount.toBigDecimal() }.toCoinStr(NHASH)
            UnpaginatedDelegation(recs, mapOf(Pair("unbondingTotal", total)))
        }
    }

    fun getRedelegations(address: String) = runBlocking {
        accountClient.getRedelegations(address, 0, 100).let { res ->
            res.redelegationResponsesList.flatMap { list ->
                list.entriesList.map {
                    Delegation(
                        list.redelegation.delegatorAddress,
                        list.redelegation.validatorSrcAddress,
                        list.redelegation.validatorDstAddress,
                        CoinStr(it.balance, NHASH),
                        CoinStr(it.redelegationEntry.initialBalance, NHASH),
                        it.redelegationEntry.sharesDst.toDecCoin(),
                        it.redelegationEntry.creationHeight.toInt(),
                        it.redelegationEntry.completionTime.toDateTime()
                    )
                }
            }
        }.let { recs ->
            val total = recs.sumOf { it.amount.amount.toBigDecimal() }.toCoinStr(NHASH)
            UnpaginatedDelegation(recs, mapOf(Pair("redelegationTotal", total)))
        }
    }

    fun getRewards(address: String) = runBlocking {
        accountClient.getRewards(address).let { res ->
            val pricing =
                assetService.getPricingInfoIn(res.rewardsList.flatMap { list -> list.rewardList.map { it.denom } })

            AccountRewards(
                res.rewardsList.map { list ->
                    Reward(
                        list.validatorAddress,
                        list.rewardList.map { r -> r.toCoinStrWithPrice(pricing[r.denom]) }
                    )
                },
                res.totalList.map { t -> t.toCoinStrWithPrice(pricing[t.denom]) }
            )
        }
    }
}

fun String.getAccountType() = this.split(".").last()

fun Attribute.toResponse(): AttributeObj {
    val data = when {
        this.name.contains("passport") -> this.value.toStringUtf8().fromBase64().toObjectNode().let { node ->
            try {
                // Try to parse out passport details
                when {
                    node["pending"].asBoolean() -> "pending"
                    node["expirationDate"].asText().toDateTime().isBeforeNow -> "expired"
                    else -> "active"
                }
            } catch (e: Exception) {
                // If it fails, just pass back the encoded string
                this.value.toStringUtf8().toBase64()
            }
        }
        else -> this.value.toStringUtf8().toBase64()
    }
    return AttributeObj(this.name, data)
}
