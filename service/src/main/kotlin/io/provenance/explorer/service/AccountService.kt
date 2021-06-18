package io.provenance.explorer.service

import io.provenance.attribute.v1.Attribute
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.AccountRecord.Companion.update
import io.provenance.explorer.domain.extensions.NHASH
import io.provenance.explorer.domain.extensions.fromBase64
import io.provenance.explorer.domain.extensions.isAddressAsType
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toBase64
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toDecCoin
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.toSigObj
import io.provenance.explorer.domain.models.explorer.AccountDetail
import io.provenance.explorer.domain.models.explorer.AccountRewards
import io.provenance.explorer.domain.models.explorer.AttributeObj
import io.provenance.explorer.domain.models.explorer.CoinStr
import io.provenance.explorer.domain.models.explorer.Delegation
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.Reward
import io.provenance.explorer.domain.models.explorer.toData
import io.provenance.explorer.grpc.extensions.getModuleAccName
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provenance.explorer.grpc.v1.AttributeGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class AccountService(
    private val accountClient: AccountGrpcClient,
    private val props: ExplorerProperties,
    private val attrClient: AttributeGrpcClient,
) {

    protected val logger = logger(AccountService::class)

    fun getAccountRaw(address: String) = transaction { AccountRecord.findByAddress(address) } ?: saveAccount(address)

    fun saveAccount(address: String) =
        accountClient.getAccountInfo(address)?.let { AccountRecord.insertIgnore(it) }
            ?: if (address.isAddressAsType(props.provAccPrefix())) AccountRecord.insertUnknownAccount(address)
                else throw ResourceNotFoundException("Invalid account: '$address'")

    fun getAccountDetail(address: String) = getAccountRaw(address).let {
        AccountDetail(
            it.type,
            it.accountAddress,
            it.accountNumber,
            it.baseAccount?.sequence?.toInt(),
            AccountRecord.findSigsByAddress(it.accountAddress).toSigObj(props.provAccPrefix()),
            it.data?.getModuleAccName(),
            attrClient.getAllAttributesForAddress(it.accountAddress).map { attr -> attr.toResponse() },
        )
    }

    fun getNamesOwnedByAccount(address: String, page: Int, limit: Int) =
        attrClient.getNamesForAddress(address, page.toOffset(limit), limit).let { res ->
            val names = res.nameList.toList()
            PagedResults(res.pagination.total.pageCountOfResults(limit), names, res.pagination.total)
        }

    fun getBalances(address: String, page: Int, limit: Int) =
        accountClient.getAccountBalances(address, page.toOffset(limit), limit)

    fun getAccountBalances(address: String, page: Int, limit: Int) =
        getBalances(address, page, limit).let { res ->
            val bals = res.balancesList.map { it.toData() }
            PagedResults(res.pagination.total.pageCountOfResults(limit), bals, res.pagination.total)
        }

    fun getCurrentSupply(denom: String) = accountClient.getCurrentSupply(denom).amount

    fun getDenomMetadataSingle(denom: String) = accountClient.getDenomMetadata(denom).metadata

    fun getDenomMetadata(denom: String?) =
        if (denom != null) listOf(accountClient.getDenomMetadata(denom).metadata)
        else accountClient.getAllDenomMetadata().metadatasList

    fun getDelegations(address: String, page: Int, limit: Int) =
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
                    null)
            }
            PagedResults(res.pagination.total.pageCountOfResults(limit), list, res.pagination.total)
        }

    fun getUnbondingDelegations(address: String) =
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
        }

    fun getRedelegations(address: String) =
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
        }

    fun getRewards(address: String) = accountClient.getRewards(address).let { res ->
        AccountRewards(
            res.rewardsList.map { list ->
                Reward(
                    list.validatorAddress,
                    list.rewardList.map { r -> CoinStr(r.amount.toDecCoin(), r.denom) })
            },
            res.totalList.map { t -> CoinStr(t.amount.toDecCoin(), t.denom) }
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
