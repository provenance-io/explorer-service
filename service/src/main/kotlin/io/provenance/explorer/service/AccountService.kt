package io.provenance.explorer.service

import com.google.protobuf.Any
import cosmos.bank.v1beta1.msgSend
import cosmos.vesting.v1beta1.msgCreateVestingAccount
import io.provenance.attribute.v1.Attribute
import io.provenance.explorer.config.ExplorerProperties.Companion.PROV_ACC_PREFIX
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.sql.toEntities
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.AccountTokenCountRecord
import io.provenance.explorer.domain.entities.MarkerUnitRecord
import io.provenance.explorer.domain.entities.SignatureRecord
import io.provenance.explorer.domain.entities.TxAddressJoinTable
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxCacheTable
import io.provenance.explorer.domain.entities.TxHistoryDataViews
import io.provenance.explorer.domain.entities.TxMessageTable
import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.entities.TxMsgTypeSubtypeTable
import io.provenance.explorer.domain.entities.ValidatorStateRecord
import io.provenance.explorer.domain.exceptions.requireNotNullToMessage
import io.provenance.explorer.domain.exceptions.requireToMessage
import io.provenance.explorer.domain.exceptions.validate
import io.provenance.explorer.domain.extensions.diff
import io.provenance.explorer.domain.extensions.getType
import io.provenance.explorer.domain.extensions.mapToProtoCoin
import io.provenance.explorer.domain.extensions.pack
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.pageOfResults
import io.provenance.explorer.domain.extensions.toBase64
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toDecimalStringOld
import io.provenance.explorer.domain.extensions.toNormalCase
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.toProtoCoin
import io.provenance.explorer.domain.extensions.typeToLabel
import io.provenance.explorer.domain.models.explorer.AddrData
import io.provenance.explorer.domain.models.explorer.download.TxHistoryDataRequest
import io.provenance.explorer.domain.models.explorer.toCoinStr
import io.provenance.explorer.domain.models.explorer.toCoinStrWithPrice
import io.provenance.explorer.grpc.extensions.getModuleAccName
import io.provenance.explorer.grpc.extensions.isIca
import io.provenance.explorer.grpc.extensions.isStandardAddress
import io.provenance.explorer.grpc.extensions.isVesting
import io.provenance.explorer.grpc.extensions.toVestingData
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provenance.explorer.grpc.v1.AttributeGrpcClient
import io.provenance.explorer.grpc.v1.MetadataGrpcClient
import io.provenance.explorer.model.AccountDetail
import io.provenance.explorer.model.AccountFlags
import io.provenance.explorer.model.AccountRewards
import io.provenance.explorer.model.AccountSigInfo
import io.provenance.explorer.model.AccountSignature
import io.provenance.explorer.model.AttributeObj
import io.provenance.explorer.model.BankSendRequest
import io.provenance.explorer.model.Delegation
import io.provenance.explorer.model.DenomBalanceBreakdown
import io.provenance.explorer.model.Reward
import io.provenance.explorer.model.TokenCounts
import io.provenance.explorer.model.UnpaginatedDelegation
import io.provenance.explorer.model.base.CoinStr
import io.provenance.explorer.model.base.PagedResults
import io.provenance.explorer.model.base.PeriodInSeconds
import io.provenance.explorer.model.base.USD_UPPER
import io.provenance.explorer.model.download.TxHistoryChartData
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.servlet.ServletOutputStream

@Service
class AccountService(
    private val accountClient: AccountGrpcClient,
    private val attrClient: AttributeGrpcClient,
    private val metadataClient: MetadataGrpcClient,
    private val pricingService: PricingService,
    private val assetService: AssetService,
    private val valService: ValidatorService
) {

    protected val logger = logger(AccountService::class)

    fun getAccountRaw(address: String) = transaction { AccountRecord.findByAddress(address) } ?: saveAccount(address)

    fun saveAccount(address: String, isContract: Boolean = false, isGroupPolicy: Boolean = false) = runBlocking {
        AccountRecord.saveAccount(address, PROV_ACC_PREFIX, accountClient.getAccountInfo(address), isContract, isGroupPolicy)
    }

    fun validateAddress(address: String) = transaction {
        requireNotNullToMessage(AccountRecord.findByAddress(address)) { "Address $address does not exist." }
    }

    fun getAccountFlags(address: String) =
        getAccountRaw(address).let {
            AccountFlags(
                it.isContract,
                it.data?.isVesting() ?: false,
                it.data?.isIca() ?: false
            )
        }

    fun getAddressDetails(addr: String) = transaction {
        val addrId = AccountRecord.findByAddress(addr)!!.id.value
        val isValidator = ValidatorStateRecord.findByAccount(valService.getActiveSet(), addr) != null
        AddrData(addr, addrId, isValidator)
    }

    fun getAccountDetail(address: String) = runBlocking {
        getAccountRaw(address).let {
            val attributes = async { attrClient.getAllAttributesForAddress(it.accountAddress) }
            val tokenCounts = transaction { it.tokenCounts.firstOrNull() }
            val balances = async { getBalances(it.accountAddress, 1, tokenCounts?.ftCount ?: 100) }
            AccountDetail(
                it.type.toNormalCase(),
                it.accountAddress,
                it.accountNumber,
                it.baseAccount?.sequence?.toInt(),
                getAccountSignatures(address),
                it.data?.getModuleAccName(),
                attributes.await().map { attr -> attr.toResponse() },
                TokenCounts(tokenCounts?.ftCount ?: 0, tokenCounts?.nftCount ?: 0),
                it.isContract,
                pricingService.getAumForList(
                    balances.await().balancesList.associate { bal -> bal.denom to bal.amount },
                    "accountAUM"
                )
                    .toCoinStr(USD_UPPER),
                it.data?.isVesting() ?: false,
                AccountFlags(it.isContract, it.data?.isVesting() ?: false, it.data?.isIca() ?: false),
                it.owner // /// do not link to anything, this is a third party chain address
            )
        }
    }

    private fun getAccountSignatures(address: String) =
        SignatureRecord.findByAddress(address)
            .let { list ->
                val firstRec = list.getOrNull(0)
                AccountSigInfo(
                    firstRec?.pubkeyType?.typeToLabel(),
                    firstRec?.base64Sig,
                    list.map {
                        AccountSignature(
                            it.childSigIdx ?: 0,
                            it.childSigAddress ?: address
                        )
                    }
                )
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

    fun getAccountBalanceForUtilityToken(address: String) = getAccountBalanceForDenomDetailed(address, UTILITY_TOKEN)

    fun getAccountBalanceForDenomDetailed(address: String, denom: String) = runBlocking {
        val unit = MarkerUnitRecord.findByUnit(denom)?.marker ?: denom
        accountClient.getAccountBalanceForDenom(address, unit).let { res ->
            val pricing = pricingService.getPricingInfoSingle(unit) ?: BigDecimal.ZERO
            val spendable = accountClient.getSpendableBalanceDenom(address, unit) ?: BigDecimal.ZERO.toProtoCoin(unit)
            DenomBalanceBreakdown(
                res.toCoinStrWithPrice(pricing),
                spendable.toCoinStrWithPrice(pricing),
                res.diff(spendable).toCoinStrWithPrice(pricing, unit)
            )
        }
    }

    // Used for balance validation - this is raw data only
    fun getAccountBalancesAllAtHeight(address: String, height: Int, denom: String?) = runBlocking {
        if (denom != null) {
            listOf(accountClient.getAccountBalanceForDenomAtHeight(address, denom, height).toCoinStr())
        } else {
            accountClient.getAccountBalancesAllAtHeight(address, height).map { it.toCoinStr() }
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
            .toCoinStr(delegations.firstOrNull()?.balance?.denom ?: UTILITY_TOKEN)
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
                    it.delegation.shares.toDecimalStringOld(),
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
                        CoinStr(it.balance, UTILITY_TOKEN),
                        CoinStr(it.initialBalance, UTILITY_TOKEN),
                        null,
                        it.creationHeight.toInt(),
                        it.completionTime.toDateTime()
                    )
                }
            }
        }.let { recs ->
            val total = recs.sumOf { it.amount.amount.toBigDecimal() }.toCoinStr(UTILITY_TOKEN)
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
                        CoinStr(it.balance, UTILITY_TOKEN),
                        CoinStr(it.redelegationEntry.initialBalance, UTILITY_TOKEN),
                        it.redelegationEntry.sharesDst.toDecimalStringOld(),
                        it.redelegationEntry.creationHeight.toInt(),
                        it.redelegationEntry.completionTime.toDateTime()
                    )
                }
            }
        }.let { recs ->
            val total = recs.sumOf { it.amount.amount.toBigDecimal() }.toCoinStr(UTILITY_TOKEN)
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
            requireToMessage(request.to.isStandardAddress()) { "to must be a standard address format" },
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
            if (acc.data == null || acc.data?.isVesting() == false) {
                throw ResourceNotFoundException("Invalid vesting account: '$address'")
            }
            acc.data!!.toVestingData(getInitializationDate(acc), continuousPeriod)
        }
    }

    // Gets the origination dateTime for the account. Currently only applicable to vesting accounts
    private fun getInitializationDate(account: AccountRecord) = transaction {
        val types = listOf(msgCreateVestingAccount { }.getType())
        val typeIds = TxMessageTypeRecord.findByProtoTypeIn(types)

        TxAddressJoinTable
            .innerJoin(TxMessageTable, { TxAddressJoinTable.txHashId }, { TxMessageTable.txHashId })
            .innerJoin(TxMsgTypeSubtypeTable, { TxMessageTable.id }, { TxMsgTypeSubtypeTable.txMsgId })
            .innerJoin(TxCacheTable, { TxAddressJoinTable.txHashId }, { TxCacheTable.id })
            .slice(TxCacheTable.columns)
            .select {
                (
                    (TxMsgTypeSubtypeTable.primaryType inList typeIds)
                        or (TxMsgTypeSubtypeTable.secondaryType inList typeIds.toEntities(TxMsgTypeSubtypeTable))
                    )
            }
            .andWhere { TxAddressJoinTable.addressId eq account.id.value }
            .andWhere { TxCacheTable.errorCode.isNull() }
            .orderBy(Pair(TxCacheTable.txTimestamp, SortOrder.ASC))
            .limit(1)
            .let { TxCacheRecord.wrapRows(it) }
            .firstOrNull()
            ?.txTimestamp
    }

    fun getAccountTxHistoryChartData(feepayer: String, filters: TxHistoryDataRequest): List<TxHistoryChartData> {
        validate(
            filters.granularityValidation(),
            filters.datesValidation()
        )
        return TxHistoryDataViews.getTxHistoryChartData(filters.granularity, filters.fromDate, filters.toDate, feepayer)
    }

    fun getAccountTxHistoryChartDataDownload(
        filters: TxHistoryDataRequest,
        feepayer: String,
        outputStream: ServletOutputStream
    ): ZipOutputStream {
        validate(
            filters.granularityValidation(),
            filters.datesValidation(),
            validateAddress(feepayer)
        )
        val baseFileName = filters.getFileNameBase(feepayer)
        val fileList = filters.getFileList(feepayer)

        val zos = ZipOutputStream(outputStream)
        fileList.forEach { file ->
            zos.putNextEntry(ZipEntry("$baseFileName - ${file.fileName}.csv"))
            zos.write(file.writeCsvEntry())
            zos.closeEntry()
        }
        // Adding in a txt file with the applied filters
        zos.putNextEntry(ZipEntry("$baseFileName - FILTERS.txt"))
        zos.write(filters.writeFilters(feepayer))
        zos.closeEntry()

        zos.close()
        return zos
    }

    suspend fun updateTokenCounts(addr: String) {
            try {
                val nftCount = metadataClient.getScopesByOwnerTotal(addr)
                val ftCount = getBalances(addr, 0, 1).pagination.total.toInt()
                AccountTokenCountRecord.upsert(addr, ftCount, nftCount)
            } catch (e: Exception) {
                logger.error("Failed to update token counts for $addr : ${e.message}")
            }
        }
}

fun String.getAccountType() = this.split(".").last()

fun Attribute.toResponse() = AttributeObj(this.name, this.value.toStringUtf8().toBase64())
