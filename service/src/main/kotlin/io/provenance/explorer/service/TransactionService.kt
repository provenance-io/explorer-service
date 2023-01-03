package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import cosmos.base.abci.v1beta1.Abci
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.BlockCacheHourlyTxCountsRecord
import io.provenance.explorer.domain.entities.MarkerUnitRecord
import io.provenance.explorer.domain.entities.SignatureTxRecord
import io.provenance.explorer.domain.entities.SmContractRecord
import io.provenance.explorer.domain.entities.TxAddressJoinRecord
import io.provenance.explorer.domain.entities.TxAddressJoinType
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxHistoryDataViews
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.entities.TxMsgTypeSubtypeRecord
import io.provenance.explorer.domain.entities.ValidatorMarketRateRecord
import io.provenance.explorer.domain.entities.ValidatorStateRecord
import io.provenance.explorer.domain.entities.getFeepayer
import io.provenance.explorer.domain.exceptions.InvalidArgumentException
import io.provenance.explorer.domain.exceptions.validate
import io.provenance.explorer.domain.extensions.formattedString
import io.provenance.explorer.domain.extensions.msgEventsToObjectNodePrint
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toFeePaid
import io.provenance.explorer.domain.extensions.toFees
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.txEventsToObjectNodePrint
import io.provenance.explorer.domain.models.explorer.TxHistoryDataRequest
import io.provenance.explorer.domain.models.explorer.TxQueryParams
import io.provenance.explorer.domain.models.explorer.datesValidation
import io.provenance.explorer.domain.models.explorer.getFileList
import io.provenance.explorer.domain.models.explorer.getValuesPlusAddtnl
import io.provenance.explorer.domain.models.explorer.granularityValidation
import io.provenance.explorer.grpc.extensions.getModuleAccName
import io.provenance.explorer.model.Gas
import io.provenance.explorer.model.MsgInfo
import io.provenance.explorer.model.MsgTypeSet
import io.provenance.explorer.model.TxDetails
import io.provenance.explorer.model.TxGov
import io.provenance.explorer.model.TxHeatmapRes
import io.provenance.explorer.model.TxHistoryChartData
import io.provenance.explorer.model.TxMessage
import io.provenance.explorer.model.TxSmartContract
import io.provenance.explorer.model.TxStatus
import io.provenance.explorer.model.TxSummary
import io.provenance.explorer.model.TxType
import io.provenance.explorer.model.base.PagedResults
import io.provenance.explorer.model.base.Timeframe
import io.provenance.explorer.model.base.getParentForType
import io.provenance.explorer.model.base.isMAddress
import io.provenance.explorer.model.base.toMAddress
import io.provenance.explorer.model.base.toMAddressScope
import io.provenance.explorer.service.async.AsyncCachingV2
import io.provenance.explorer.service.async.getAddressType
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.servlet.ServletOutputStream

@Service
class TransactionService(
    private val protoPrinter: JsonFormat.Printer,
    private val asyncV2: AsyncCachingV2,
    private val nftService: NftService,
    private val valService: ValidatorService,
    private val ibcService: IbcService
) {

    protected val logger = logger(TransactionService::class)

    private fun getTxByHashFromCache(hash: String) = transaction { TxCacheRecord.findByHash(hash) }

    fun getTxTypes(typeSet: MsgTypeSet?) = transaction {
        when (typeSet) {
            null -> TxMessageTypeRecord.all()
            else -> TxMessageTypeRecord.findByType(typeSet.types)
        }.toList().mapToRes()
    }

    fun getTxTypesByTxHash(txHash: String, blockHeight: Int? = null) = transaction {
        getTxByHash(txHash)
            .ifEmpty { throw ResourceNotFoundException("Invalid transaction hash: '$txHash'") }
            .getMainState(blockHeight)
            .let { tx ->
                TxMessageRecord.getDistinctTxMsgTypesByTxHash(tx.id).let { TxMessageTypeRecord.findByIdIn(it) }
                    .mapToRes()
            }
    }

    fun getTxsByQuery(
        address: String? = null,
        denom: String? = null,
        module: MsgTypeSet? = null,
        msgType: String? = null,
        txHeight: Int? = null,
        txStatus: TxStatus? = null,
        count: Int,
        page: Int,
        fromDate: DateTime? = null,
        toDate: DateTime? = null,
        nftAddr: String? = null,
        ibcChain: String? = null,
        ibcSrcPort: String? = null,
        ibcSrcChannel: String? = null
    ): PagedResults<TxSummary> {
        val msgTypes = if (msgType != null) listOf(msgType) else (module?.getValuesPlusAddtnl() ?: listOf())
        val msgTypeIds = transaction { TxMessageTypeRecord.findByType(msgTypes).map { it.id.value } }.toList()
        val addr = transaction { address?.getAddressType(valService.getActiveSet()) }
        val markerId = if (denom != null) MarkerUnitRecord.findByUnit(denom)?.markerId else null
        val nftMAddress = if (nftAddr != null && nftAddr.isMAddress()) nftAddr.toMAddress() else nftAddr?.toMAddressScope()
        val nft = nftMAddress?.let { Triple(it.getParentForType()?.name, nftService.getNftDbId(it), it.getPrimaryUuid().toString()) }
        val ibcChannelIds =
            if (ibcSrcPort != null && ibcSrcChannel != null) {
                listOf(ibcService.getChannelIdByPortAndChannel(ibcSrcPort, ibcSrcChannel))
            } else if (ibcChain != null) {
                ibcService.getChannelIdsByChain(ibcChain)
            } else {
                emptyList()
            }

        val params =
            TxQueryParams(
                addressId = addr?.second, addressType = addr?.first, address = address, markerId = markerId,
                denom = denom, msgTypes = msgTypeIds, txHeight = txHeight, txStatus = txStatus, count = count,
                offset = page.toOffset(count), fromDate = fromDate, toDate = toDate, nftId = nft?.second,
                nftType = nft?.first, nftUuid = nft?.third, ibcChannelIds = ibcChannelIds
            )

        val total = TxCacheRecord.findByQueryParamsForCount(params)
        TxCacheRecord.findByQueryForResults(params).map {
            val rec = it
            val displayMsgType = transaction {
                if (msgTypes.isNotEmpty()) {
                    rec.txMessages.flatMap { msg -> msg.txMessageType }.firstMatchLabel(msgTypes)
                } else {
                    rec.txMessages.first().txMessageType.firstMatchLabel()
                }
            }
            TxSummary(
                rec.hash,
                rec.height,
                MsgInfo(transaction { rec.txMessages.count() }, displayMsgType),
                getMonikers(rec.id),
                rec.txTimestamp.toString(),
                transaction { rec.txFees.filter { fee -> fee.marker == UTILITY_TOKEN }.toFeePaid(UTILITY_TOKEN) },
                getTxSignatures(rec.id.value),
                if (rec.errorCode == null) "success" else "failed",
                transaction { rec.txFeepayer.getFeepayer() }
            )
        }.let { return PagedResults(total.pageCountOfResults(count), it, total.toLong()) }
    }

    private fun getTxSignatures(txHashId: Int) = SignatureTxRecord.findByTxHashId(txHashId)

    fun MutableList<TxMessageRecord>.mapToTxMessages(logs: MutableList<Abci.ABCIMessageLog>) =
        this.map { msg ->
            val logSet = logs.getOrNull(msg.msgIdx)?.eventsList ?: emptyList()
            TxMessage(
                msg.txMessageType.toList().firstMatchLabel(),
                msg.txMessage.toObjectNode(protoPrinter),
                logSet.msgEventsToObjectNodePrint(protoPrinter)
            )
        }

    private fun getTxByHash(hash: String) = getTxByHashFromCache(hash)

    fun getTransactionJson(txnHash: String, blockHeight: Int? = null) =
        getTxByHash(txnHash)
            .ifEmpty { throw ResourceNotFoundException("Invalid transaction hash: '$txnHash'") }
            .getMainState(blockHeight).txV2.let { protoPrinter.print(it) }

    fun getTransactionByHash(hash: String, blockHeight: Int? = null) =
        getTxByHash(hash)
            .ifEmpty { throw ResourceNotFoundException("Invalid transaction hash: '$hash'") }
            .let { list ->
                val state = list.getMainState(blockHeight)

                hydrateTxDetails(state)
                    .apply { this.additionalHeights = list.filterNot { it.height == state.height }.map { it.height } }
            }

    private fun hydrateTxDetails(tx: TxCacheRecord) = transaction {
        TxDetails(
            txHash = tx.txV2.txResponse.txhash,
            height = tx.txV2.txResponse.height.toInt(),
            gas = Gas(
                tx.txV2.txResponse.gasUsed,
                tx.txV2.txResponse.gasWanted,
                ValidatorMarketRateRecord.getRateByTxId(tx.id.value).toCoinStr(UTILITY_TOKEN)
            ),
            time = asyncV2.getBlock(tx.txV2.txResponse.height.toInt())!!.block.header.time.formattedString(),
            status = if (tx.txV2.txResponse.code > 0) "failed" else "success",
            errorCode = tx.txV2.txResponse.code,
            codespace = tx.txV2.txResponse.codespace,
            errorLog = if (tx.txV2.txResponse.code > 0) tx.txV2.txResponse.rawLog else null,
            fee = tx.txFees.toList().toFees(),
            signers = getTxSignatures(tx.id.value),
            memo = tx.txV2.tx.body.memo,
            monikers = getMonikers(tx.id),
            feepayer = tx.txFeepayer.getFeepayer(),
            associatedValues = TxCacheRecord.getAssociatedValues(tx.hash, tx.height),
            events = tx.txV2.txResponse.eventsList.txEventsToObjectNodePrint(protoPrinter)
        )
    }

    fun getTxMsgsPaginated(hash: String, msgType: String?, page: Int, count: Int, blockHeight: Int? = null) =
        transaction {
            val msgTypes = if (msgType != null) listOf(msgType) else listOf()
            val msgTypeIds = transaction { TxMessageTypeRecord.findByType(msgTypes).map { it.id.value } }.toList()

            TxCacheRecord.findByHash(hash)
                .ifEmpty { throw ResourceNotFoundException("Invalid transaction hash: '$hash'") }
                .getMainState(blockHeight)
                .let { tx ->
                    val msgs =
                        TxMessageRecord.findByHashIdPaginated(tx.id.value, msgTypeIds, count, page.toOffset(count))
                            .mapToTxMessages(tx.txV2.txResponse.logsList)
                    val total = TxMessageRecord.getCountByHashId(tx.id.value, msgTypeIds)
                    PagedResults(total.pageCountOfResults(count), msgs, total.toLong())
                }
        }

    fun getTxHeatmap(fromDate: DateTime? = null, toDate: DateTime? = null, timeframe: Timeframe = Timeframe.FOREVER): TxHeatmapRes {
        if (fromDate != null && toDate != null)
            return BlockCacheHourlyTxCountsRecord.getTxHeatmap(fromDate, toDate)

        val (from, to) = when (timeframe) {
            Timeframe.FOREVER -> null to null
            Timeframe.QUARTER ->
                if (fromDate != null) fromDate to fromDate.plusMonths(3)
                else if (toDate != null) toDate.minusMonths(3) to toDate
                else DateTime.now().startOfDay().let { it.minusMonths(3) to it }
            Timeframe.MONTH ->
                if (fromDate != null) fromDate to fromDate.plusMonths(1)
                else if (toDate != null) toDate.minusMonths(1) to toDate
                else DateTime.now().startOfDay().let { it.minusMonths(1) to it }
            Timeframe.WEEK ->
                if (fromDate != null) fromDate to fromDate.plusWeeks(1)
                else if (toDate != null) toDate.minusWeeks(1) to toDate
                else DateTime.now().startOfDay().let { it.minusWeeks(1) to it }
            Timeframe.DAY, Timeframe.HOUR ->
                throw InvalidArgumentException("Timeframe ${timeframe.name} is not supported for heatmap data")
        }

        return BlockCacheHourlyTxCountsRecord.getTxHeatmap(from, to)
    }

    private fun getMonikers(txId: EntityID<Int>): Map<String, String> {
        val monikers = TxAddressJoinRecord.findValidatorsByTxHash(valService.getActiveSet(), txId)
            .associate { v -> v.operatorAddress to v.moniker }
        val moduleNames =
            TxAddressJoinRecord.findAccountsByTxHash(txId)
                .filter { it.type == "ModuleAccount" }
                .associate { a -> a.accountAddress to a.data!!.getModuleAccName()!! }

        return monikers + moduleNames
    }

    fun getGovernanceTxs(
        address: String?,
        msgType: String?,
        txStatus: TxStatus?,
        page: Int,
        count: Int,
        fromDate: DateTime?,
        toDate: DateTime?
    ): PagedResults<TxGov> =
        transaction {
            val msgTypes = if (msgType != null) listOf(msgType) else MsgTypeSet.GOVERNANCE.types
            val msgTypeIds = transaction { TxMessageTypeRecord.findByType(msgTypes).map { it.id.value } }.toList()
            val addr = transaction {
                var pair = address?.getAddressType(valService.getActiveSet())
                if (pair?.first == TxAddressJoinType.OPERATOR.name) {
                    val accAddr = ValidatorStateRecord.findByOperator(valService.getActiveSet(), address!!)?.accountAddr
                    val accId = AccountRecord.findByAddress(accAddr!!)?.id?.value
                    pair = Pair(TxAddressJoinType.ACCOUNT.name, accId)
                }
                pair
            }

            val params =
                TxQueryParams(
                    addressId = addr?.second, addressType = addr?.first, address = address, msgTypes = msgTypeIds,
                    txStatus = txStatus, count = count, offset = page.toOffset(count), fromDate = fromDate, toDate = toDate
                )

            val total = TxMessageRecord.findByQueryParamsForCount(params)
            TxMessageRecord.findByQueryForResults(params).map { msg ->
                val govDetail = msg.txMessage.getGovMsgDetail(msg.txHash)!!
                TxGov(
                    msg.txHash,
                    msg.txMessageType.firstMatchLabel(msgTypes),
                    govDetail.depositAmount,
                    govDetail.proposalType,
                    govDetail.proposalId,
                    govDetail.proposalTitle,
                    msg.blockHeight,
                    msg.txHashId.txTimestamp.toString(),
                    transaction { msg.txHashId.txFees.filter { fee -> fee.marker == UTILITY_TOKEN }.toFeePaid(UTILITY_TOKEN) },
                    getTxSignatures(msg.txHashId.id.value),
                    if (msg.txHashId.errorCode == null) "success" else "failed",
                    transaction { msg.txHashId.txFeepayer.getFeepayer() }
                )
            }.let { PagedResults(total.pageCountOfResults(count), it, total.toLong()) }
        }

    fun getSmartContractTxs(
        codeId: Int?,
        contract: String?,
        msgType: String?,
        txStatus: TxStatus?,
        page: Int,
        count: Int,
        fromDate: DateTime?,
        toDate: DateTime?
    ) = transaction {
        val msgTypes = if (msgType != null) listOf(msgType) else MsgTypeSet.SMART_CONTRACT.types
        val msgTypeIds = transaction { TxMessageTypeRecord.findByType(msgTypes).map { it.id.value } }.toList()
        val contractId = contract?.let { SmContractRecord.findByContractAddress(it)?.id?.value }

        val params =
            TxQueryParams(
                msgTypes = msgTypeIds,
                txStatus = txStatus,
                count = count,
                offset = page.toOffset(count),
                fromDate = fromDate,
                toDate = toDate,
                smCodeId = codeId,
                smContractAddrId = contractId
            )

        val total = TxMessageRecord.findByQueryParamsForCount(params)
        TxMessageRecord.findByQueryForResults(params).map { msg ->
            val scDetail = msg.txMessage.getScMsgDetail(msg.msgIdx, msg.txHashId.txV2)!!
            TxSmartContract(
                msg.txHash,
                msg.txMessageType.firstMatchLabel(msgTypes),
                scDetail.first,
                scDetail.second,
                msg.blockHeight,
                msg.txHashId.txTimestamp.toString(),
                transaction { msg.txHashId.txFees.filter { fee -> fee.marker == UTILITY_TOKEN }.toFeePaid(UTILITY_TOKEN) },
                getTxSignatures(msg.txHashId.id.value),
                if (msg.txHashId.errorCode == null) "success" else "failed",
                transaction { msg.txHashId.txFeepayer.getFeepayer() }
            )
        }.let { PagedResults(total.pageCountOfResults(count), it, total.toLong()) }
    }

    fun getTxHistoryChartData(filters: TxHistoryDataRequest): List<TxHistoryChartData> {
        validate(
            granularityValidation(filters.granularity),
            datesValidation(filters.fromDate, filters.toDate)
        )
        return TxHistoryDataViews.getTxHistoryChartData(filters.granularity, filters.fromDate, filters.toDate)
    }

    fun getTxHistoryChartDataDownload(filters: TxHistoryDataRequest, resp: ServletOutputStream): ZipOutputStream {
        validate(
            granularityValidation(filters.granularity),
            datesValidation(filters.fromDate, filters.toDate)
        )
        val baseFileName = filters.getFileNameBase(null)
        val fileList = getFileList(filters, null)

        val zos = ZipOutputStream(resp)
        fileList.forEach { file ->
            zos.putNextEntry(ZipEntry("$baseFileName - ${file.fileName}.csv"))
            zos.write(file.writeCsvEntry())
            zos.closeEntry()
        }
        // Adding in a txt file with the applied filters
        zos.putNextEntry(ZipEntry("$baseFileName - FILTERS.txt"))
        zos.write(filters.writeFilters(null))
        zos.closeEntry()
        zos.close()
        return zos
    }
}

fun List<TxMessageTypeRecord>.mapToRes() =
    this.map { TxType(it.category ?: it.module, it.type) }.toSet().sortedWith(compareBy(TxType::module, TxType::type))

fun SizedIterable<TxMsgTypeSubtypeRecord>.firstMatchLabel(filter: List<String> = emptyList()) = this.toList().firstMatchLabel(filter)

fun List<TxMsgTypeSubtypeRecord>.firstMatchLabel(filter: List<String> = emptyList()) =
    if (filter.isNotEmpty()) {
        this.toList().groupedByTxMsg()
            .filter { (_, v) -> filter.intersect((v.mapPieces(true) + v.mapPieces(false)).toSet()).isNotEmpty() }
            .groupedByType().makeLabels()[0]
    } else {
        this.toList().groupedByTxMsg().groupedByType().makeLabels()[0]
    }

fun Map<TxMessageTypeRecord, List<TxMessageTypeRecord?>>.makeLabels() =
    this.map { (k, v) ->
        v.filterNotNull().let { list ->
            if (list.isNotEmpty()) {
                list.joinToString(", ", " - ") { it.type }
            } else {
                ""
            }
        }.let { "${k.type}$it" }
    }

fun List<TxMsgTypeSubtypeRecord>.mapPieces(mapFirst: Boolean) =
    if (mapFirst) this.map { it.primaryType.type } else this.mapNotNull { it.secondaryType?.type }

fun List<TxMsgTypeSubtypeRecord>.groupedByTxMsg() = this.groupBy { it.txMsgId }
fun Map<EntityID<Int>, List<TxMsgTypeSubtypeRecord>>.groupedByType() =
    this.entries.first().value.groupBy({ it.primaryType }, { it.secondaryType })

fun List<TxCacheRecord>.getMainState(blockHeight: Int? = null) =
    if (blockHeight != null) this.first { it.height == blockHeight } else this.first()
