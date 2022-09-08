package io.provenance.explorer.service

import com.google.protobuf.Any
import cosmos.bank.v1beta1.msgSend
import cosmos.vesting.v1beta1.msgCreateVestingAccount
import io.provenance.attribute.v1.Attribute
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.TxAddressJoinTable
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxCacheTable
import io.provenance.explorer.domain.entities.TxMessageTable
import io.provenance.explorer.domain.entities.TxMessageTypeTable
import io.provenance.explorer.domain.exceptions.requireNotNullToMessage
import io.provenance.explorer.domain.exceptions.requireToMessage
import io.provenance.explorer.domain.exceptions.validate
import io.provenance.explorer.domain.extensions.USD_UPPER
import io.provenance.explorer.domain.extensions.diff
import io.provenance.explorer.domain.extensions.fromBase64
import io.provenance.explorer.domain.extensions.getType
import io.provenance.explorer.domain.extensions.pack
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.pageOfResults
import io.provenance.explorer.domain.extensions.toAccountPubKey
import io.provenance.explorer.domain.extensions.toBase64
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toDecimalString
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.AccountDetail
import io.provenance.explorer.domain.models.explorer.AccountRewards
import io.provenance.explorer.domain.models.explorer.AttributeObj
import io.provenance.explorer.domain.models.explorer.BankSendRequest
import io.provenance.explorer.domain.models.explorer.CoinStr
import io.provenance.explorer.domain.models.explorer.Delegation
import io.provenance.explorer.domain.models.explorer.DenomBalanceBreakdown
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.PeriodInSeconds
import io.provenance.explorer.domain.models.explorer.Reward
import io.provenance.explorer.domain.models.explorer.TokenCounts
import io.provenance.explorer.domain.models.explorer.UnpaginatedDelegation
import io.provenance.explorer.domain.models.explorer.mapToProtoCoin
import io.provenance.explorer.domain.models.explorer.toCoinStrWithPrice
import io.provenance.explorer.domain.models.explorer.toData
import io.provenance.explorer.grpc.extensions.getModuleAccName
import io.provenance.explorer.grpc.extensions.isStandardAddress
import io.provenance.explorer.grpc.extensions.isVesting
import io.provenance.explorer.grpc.extensions.toVestingData
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provenance.explorer.grpc.v1.AttributeGrpcClient
import io.provenance.explorer.grpc.v1.MetadataGrpcClient
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class AccountService(
    private val accountClient: AccountGrpcClient,
    private val props: ExplorerProperties,
    private val attrClient: AttributeGrpcClient,
    private val metadataClient: MetadataGrpcClient,
    private val pricingService: PricingService,
    private val assetService: AssetService
) {

    protected val logger = logger(AccountService::class)

    fun getAccountRaw(address: String) = transaction { AccountRecord.findByAddress(address) } ?: saveAccount(address)

    fun saveAccount(address: String, isContract: Boolean = false) = runBlocking {
        AccountRecord.saveAccount(address, props.provAccPrefix(), accountClient.getAccountInfo(address), isContract)
    }

    fun validateAddress(address: String) = transaction {
        requireNotNullToMessage(AccountRecord.findByAddress(address)) { "Address $address does not exist." }
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
                pricingService.getAumForList(
                    balances.await().balancesList.associate { bal -> bal.denom to bal.amount },
                    "accountAUM"
                )
                    .toCoinStr(USD_UPPER),
                it.data?.isVesting() ?: false
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

    suspend fun getBalancesAll(address: String) = accountClient.getAccountBalancesAll(address)

    suspend fun getSpendableBalances(address: String) = accountClient.getSpendableBalancesAll(address)

    @Deprecated("Use AccountService.getAccountBalancesDetailed")
    fun getAccountBalances(address: String, page: Int, limit: Int) = runBlocking {
        getBalances(address, page, limit).let { res ->
            val pricing = pricingService.getPricingInfoIn(res.balancesList.map { it.denom }, "accountBalances")
            val bals = res.balancesList.map { it.toCoinStrWithPrice(pricing[it.denom]) }
            PagedResults(res.pagination.total.pageCountOfResults(limit), bals, res.pagination.total)
        }
    }

    fun getAccountBalancesDetailed(address: String, page: Int, limit: Int) = runBlocking {
        getBalancesAll(address).let { res ->
            val pricing = pricingService.getPricingInfoIn(res.map { it.denom }, "accountBalances")
            val spendable = getSpendableBalances(address).associateBy { it.denom }
            val bals = res.map {
                DenomBalanceBreakdown(
                    it.toCoinStrWithPrice(pricing[it.denom]),
                    spendable[it.denom]!!.toCoinStrWithPrice(pricing[it.denom]),
                    it.diff(spendable[it.denom]!!).toCoinStrWithPrice(pricing[it.denom], it.denom)
                )
            }.sortedByDescending { it.total.totalBalancePrice?.amount?.toBigDecimal() ?: BigDecimal.ZERO }
            PagedResults(
                bals.count().toLong().pageCountOfResults(limit),
                bals.pageOfResults(page, limit),
                bals.count().toLong()
            )
        }
    }

    // Used for balance validation - this is raw data only
    fun getAccountBalancesAllAtHeight(address: String, height: Int) = runBlocking {
        accountClient.getAccountBalancesAllAtHeight(address, height).map { it.toData() }
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
                    it.delegation.shares.toDecimalString(),
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
                        it.redelegationEntry.sharesDst.toDecimalString(),
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
                pricingService.getPricingInfoIn(
                    res.rewardsList.flatMap { list -> list.rewardList.map { it.denom } },
                    "rewards"
                )

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

    fun createSend(request: BankSendRequest): Any {
        validate(
            validateAddress(request.from),
            requireToMessage(request.to.isStandardAddress(props)) { "to must be a standard address format" },
            requireToMessage(request.to != request.from) { "The to address must be different that the from address" },
            *request.funds.map { assetService.validateDenom(it.denom) }.toTypedArray(),
            requireToMessage(request.funds.none { it.amount.toBigDecimal() == BigDecimal.ZERO }) { "At least one deposit must have an amount greater than zero." }
        )
        return msgSend {
            fromAddress = request.from
            toAddress = request.to
            amount.addAll(request.funds.mapToProtoCoin())
        }.pack()
    }

    fun getVestingSchedule(address: String, continuousPeriod: PeriodInSeconds = PeriodInSeconds.DAY) = transaction {
        getAccountRaw(address).let { acc ->
            if (acc.data == null || acc.data?.isVesting() == false)
                throw ResourceNotFoundException("Invalid vesting account: '$address'")
            acc.data!!.toVestingData(getInitializationDate(acc), continuousPeriod)
        }
    }

    // Gets the origination dateTime for the account. Currently only applicable to vesting accounts
    private fun getInitializationDate(account: AccountRecord) = transaction {
        val types = listOf(msgCreateVestingAccount { }.getType())

        TxAddressJoinTable
            .innerJoin(TxMessageTable, { TxAddressJoinTable.txHashId }, { TxMessageTable.txHashId })
            .innerJoin(TxMessageTypeTable, { TxMessageTable.txMessageType }, { TxMessageTypeTable.id })
            .innerJoin(TxCacheTable, { TxAddressJoinTable.txHashId }, { TxCacheTable.id })
            .slice(TxCacheTable.columns)
            .select { TxMessageTypeTable.protoType inList types }
            .andWhere { TxAddressJoinTable.addressId eq account.id.value }
            .andWhere { TxCacheTable.errorCode.isNull() }
            .orderBy(Pair(TxCacheTable.txTimestamp, SortOrder.ASC))
            .limit(1)
            .let { TxCacheRecord.wrapRows(it) }
            .firstOrNull()
            ?.txTimestamp
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
