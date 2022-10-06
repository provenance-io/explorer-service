package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.getParentForType
import io.provenance.explorer.domain.core.isMAddress
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.toMAddress
import io.provenance.explorer.domain.core.toMAddressScope
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.BlockCacheHourlyTxCountsRecord
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.SmContractRecord
import io.provenance.explorer.domain.entities.TxAddressJoinRecord
import io.provenance.explorer.domain.entities.TxAddressJoinType
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.entities.ValidatorMarketRateRecord
import io.provenance.explorer.domain.entities.ValidatorStateRecord
import io.provenance.explorer.domain.entities.getFeepayer
import io.provenance.explorer.domain.extensions.formattedString
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toFeePaid
import io.provenance.explorer.domain.extensions.toFees
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.toSigObj
import io.provenance.explorer.domain.models.explorer.DateTruncGranularity
import io.provenance.explorer.domain.models.explorer.Gas
import io.provenance.explorer.domain.models.explorer.MsgInfo
import io.provenance.explorer.domain.models.explorer.MsgTypeSet
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.TxDetails
import io.provenance.explorer.domain.models.explorer.TxGov
import io.provenance.explorer.domain.models.explorer.TxMessage
import io.provenance.explorer.domain.models.explorer.TxQueryParams
import io.provenance.explorer.domain.models.explorer.TxSmartContract
import io.provenance.explorer.domain.models.explorer.TxStatus
import io.provenance.explorer.domain.models.explorer.TxSummary
import io.provenance.explorer.domain.models.explorer.TxType
import io.provenance.explorer.domain.models.explorer.getValuesPlusAddtnl
import io.provenance.explorer.grpc.extensions.getModuleAccName
import io.provenance.explorer.service.async.AsyncCachingV2
import io.provenance.explorer.service.async.getAddressType
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service

@Service
class TransactionService(
    private val protoPrinter: JsonFormat.Printer,
    private val props: ExplorerProperties,
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
        val msgTypes = if (msgType != null) listOf(msgType) else module?.getValuesPlusAddtnl() ?: listOf()
        val msgTypeIds = transaction { TxMessageTypeRecord.findByType(msgTypes).map { it.id.value } }.toList()
        val addr = transaction { address?.getAddressType(valService.getActiveSet(), props) }
        val markerId = if (denom != null) MarkerCacheRecord.findByDenom(denom)?.id?.value else null
        val nftMAddress = if (nftAddr != null && nftAddr.isMAddress()) nftAddr.toMAddress() else nftAddr?.toMAddressScope()
        val nft = nftMAddress?.let { Triple(it.getParentForType()?.name, nftService.getNftDbId(it), it.getPrimaryUuid().toString()) }
        val ibcChannelIds =
            if (ibcSrcPort != null && ibcSrcChannel != null)
                listOf(ibcService.getChannelIdByPortAndChannel(ibcSrcPort, ibcSrcChannel))
            else if (ibcChain != null) ibcService.getChannelIdsByChain(ibcChain)
            else emptyList()

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
                if (msgTypes.isNotEmpty())
                    rec.txMessages.map { msg -> msg.txMessageType.type }.first { type -> msgTypes.contains(type) }
                else rec.txMessages.first().txMessageType.type
            }
            TxSummary(
                rec.hash,
                rec.height,
                MsgInfo(transaction { rec.txMessages.count() }, displayMsgType),
                getMonikers(rec.id),
                rec.txTimestamp.toString(),
                transaction { rec.txFees.filter { fee -> fee.marker == NHASH }.toFeePaid(NHASH) },
                TxCacheRecord.findSigsByHash(rec.hash).toSigObj(props.provAccPrefix()),
                if (rec.errorCode == null) "success" else "failed",
                transaction { rec.txFeepayer.getFeepayer() }
            )
        }.let { return PagedResults(total.pageCountOfResults(count), it, total.toLong()) }
    }

    fun MutableList<TxMessageRecord>.mapToTxMessages() =
        this.map { msg -> TxMessage(msg.txMessageType.type, msg.txMessage.toObjectNode(protoPrinter)) }

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
                ValidatorMarketRateRecord.getRateByTxId(tx.id.value).toCoinStr(NHASH)
            ),
            time = asyncV2.getBlock(tx.txV2.txResponse.height.toInt())!!.block.header.time.formattedString(),
            status = if (tx.txV2.txResponse.code > 0) "failed" else "success",
            errorCode = tx.txV2.txResponse.code,
            codespace = tx.txV2.txResponse.codespace,
            errorLog = if (tx.txV2.txResponse.code > 0) tx.txV2.txResponse.rawLog else null,
            fee = tx.txFees.toList().toFees(),
            signers = TxCacheRecord.findSigsByHash(tx.txV2.txResponse.txhash).toSigObj(props.provAccPrefix()),
            memo = tx.txV2.tx.body.memo,
            monikers = getMonikers(tx.id),
            feepayer = tx.txFeepayer.getFeepayer(),
            associatedValues = TxCacheRecord.getAssociatedValues(tx.hash, tx.height)
        )
    }

    fun getTxMsgsPaginated(hash: String, msgType: String?, page: Int, count: Int, blockHeight: Int? = null) =
        transaction {
            val msgTypes = if (msgType != null) listOf(msgType) else listOf()
            val msgTypeIds = transaction { TxMessageTypeRecord.findByType(msgTypes).map { it.id.value } }.toList()

            TxCacheRecord.findByHash(hash)
                .ifEmpty { throw ResourceNotFoundException("Invalid transaction hash: '$hash'") }
                .getMainState(blockHeight)
                .id.value.let { id ->
                    val msgs = TxMessageRecord.findByHashIdPaginated(id, msgTypeIds, count, page.toOffset(count))
                        .mapToTxMessages()
                    val total = TxMessageRecord.getCountByHashId(id, msgTypeIds)
                    PagedResults(total.pageCountOfResults(count), msgs, total)
                }
        }

    fun getTxHeatmap() = BlockCacheHourlyTxCountsRecord.getTxHeatmap()

    fun getTxHistoryByQuery(fromDate: DateTime, toDate: DateTime, granularity: DateTruncGranularity) =
        BlockCacheHourlyTxCountsRecord.getTxCountsForParams(fromDate, toDate, granularity)

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
                var pair = address?.getAddressType(valService.getActiveSet(), props)
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
                    msg.txMessageType.type,
                    govDetail.depositAmount,
                    govDetail.proposalType,
                    govDetail.proposalId,
                    govDetail.proposalTitle,
                    msg.blockHeight,
                    msg.txHashId.txTimestamp.toString(),
                    transaction { msg.txHashId.txFees.filter { fee -> fee.marker == NHASH }.toFeePaid(NHASH) },
                    TxCacheRecord.findSigsByHash(msg.txHash).toSigObj(props.provAccPrefix()),
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
                msgTypes = msgTypeIds, txStatus = txStatus, count = count, offset = page.toOffset(count),
                fromDate = fromDate, toDate = toDate, smCodeId = codeId, smContractAddrId = contractId
            )

        val total = TxMessageRecord.findByQueryParamsForCount(params)
        TxMessageRecord.findByQueryForResults(params).map { msg ->
            val scDetail = msg.txMessage.getScMsgDetail(msg.msgIdx, msg.txHashId.txV2)!!
            TxSmartContract(
                msg.txHash,
                msg.txMessageType.type,
                scDetail.first,
                scDetail.second,
                msg.blockHeight,
                msg.txHashId.txTimestamp.toString(),
                transaction { msg.txHashId.txFees.filter { fee -> fee.marker == NHASH }.toFeePaid(NHASH) },
                TxCacheRecord.findSigsByHash(msg.txHash).toSigObj(props.provAccPrefix()),
                if (msg.txHashId.errorCode == null) "success" else "failed",
                transaction { msg.txHashId.txFeepayer.getFeepayer() }
            )
        }.let { PagedResults(total.pageCountOfResults(count), it, total.toLong()) }
    }
}

fun List<TxMessageTypeRecord>.mapToRes() =
    this.map { TxType(it.category ?: it.module, it.type) }.toSet().sortedWith(compareBy(TxType::module, TxType::type))

fun List<TxCacheRecord>.getMainState(blockHeight: Int? = null) =
    if (blockHeight != null) this.first { it.height == blockHeight } else this.first()
