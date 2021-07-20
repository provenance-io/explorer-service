package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.getParentForType
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.toMAddress
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.TxAddressJoinRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxCacheTable
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.extensions.formattedString
import io.provenance.explorer.domain.extensions.getMinGasFee
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.toProtoCoin
import io.provenance.explorer.domain.extensions.toSigObj
import io.provenance.explorer.domain.models.explorer.CoinStr
import io.provenance.explorer.domain.models.explorer.DateTruncGranularity
import io.provenance.explorer.domain.models.explorer.Gas
import io.provenance.explorer.domain.models.explorer.MsgInfo
import io.provenance.explorer.domain.models.explorer.MsgTypeSet
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.TxDetails
import io.provenance.explorer.domain.models.explorer.TxMessage
import io.provenance.explorer.domain.models.explorer.TxQueryParams
import io.provenance.explorer.domain.models.explorer.TxStatus
import io.provenance.explorer.domain.models.explorer.TxSummary
import io.provenance.explorer.domain.models.explorer.TxType
import io.provenance.explorer.grpc.extensions.getModuleAccName
import io.provenance.explorer.service.async.AsyncCaching
import io.provenance.explorer.service.async.getAddressType
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service

@Service
class TransactionService(
    private val protoPrinter: JsonFormat.Printer,
    private val props: ExplorerProperties,
    private val asyncCache: AsyncCaching,
    private val nftService: NftService
) {

    protected val logger = logger(TransactionService::class)

    private fun getTxByHashFromCache(hash: String) =
        transaction {
            TxCacheRecord.findByHash(hash)?.let {
                val rec = checkMsgCount(it)
                Pair(rec.id, rec.txV2)
            }
        }

    fun getTxTypes(typeSet: MsgTypeSet?) = transaction {
        when (typeSet) {
            null -> TxMessageTypeRecord.all()
            else -> TxMessageTypeRecord.findByType(typeSet.types)
        }.toList().mapToRes()
    }

    fun getTxTypesByTxHash(txHash: String) = transaction {
        getTxByHash(txHash)?.let { tx ->
            TxMessageRecord.getDistinctTxMsgTypesByTxHash(tx.first).let { TxMessageTypeRecord.findByIdIn(it) }.mapToRes()
        } ?: throw ResourceNotFoundException("Invalid transaction hash: '$txHash'")
    }

    fun getTxsByQuery(
        address: String?,
        denom: String?,
        module: MsgTypeSet?,
        msgType: String?,
        txHeight: Int?,
        txStatus: TxStatus?,
        count: Int,
        page: Int,
        fromDate: DateTime?,
        toDate: DateTime?,
        nftAddr: String?
    ): PagedResults<TxSummary> {
        val msgTypes = if (msgType != null) listOf(msgType) else module?.types ?: listOf()
        val msgTypeIds = transaction { TxMessageTypeRecord.findByType(msgTypes).map { it.id.value } }.toList()
        val addr = transaction { address?.getAddressType(props) }
        val markerId = if (denom != null) MarkerCacheRecord.findByDenom(denom)?.id?.value else null
        val nft = nftAddr?.toMAddress()
            ?.let { Triple(it.getParentForType()?.name, nftService.getNftDbId(it), it.getPrimaryUuid().toString()) }

        val params =
            TxQueryParams(
                addr?.second, addr?.first, address, markerId, denom, msgTypeIds, txHeight, txStatus,
                count, page.toOffset(count), fromDate, toDate, nft?.second, nft?.first, nft?.third
            )

        val total = TxCacheRecord.findByQueryParamsForCount(params)
        TxCacheRecord.findByQueryForResults(params).map {
            val rec = checkMsgCount(it)
            val displayMsgType = if (msgTypes.isNotEmpty()) msgTypes.first()
            else transaction { rec.txMessages.first().txMessageType.type }
            TxSummary(
                rec.hash,
                rec.height,
                MsgInfo(transaction { rec.txMessages.count() }, displayMsgType),
                getMonikers(rec.id),
                rec.txTimestamp.toString(),
                rec.txV2.tx.authInfo.fee.amountList.toProtoCoin().let { coin -> CoinStr(coin.amount, coin.denom) },
                TxCacheRecord.findSigsByHash(rec.hash).toSigObj(props.provAccPrefix()),
                if (rec.errorCode == null) "success" else "failed"
            )
        }.let { return PagedResults(total.pageCountOfResults(count), it, total.toLong()) }
    }

    // Triple checks that the tx messages are up to date in the db
    fun checkMsgCount(curr: TxCacheRecord): TxCacheRecord =
        if (transaction { curr.txMessages.count() != curr.txV2.tx.body.messagesCount.toLong() }) {
            asyncCache.addTxToCache(curr.txV2, curr.txTimestamp)
            TxCacheRecord.findByEntityId(curr.id)!!
        } else curr

    fun MutableList<TxMessageRecord>.mapToTxMessages() =
        this.map { msg -> TxMessage(msg.txMessageType.type, msg.txMessage.toObjectNode(protoPrinter)) }

    private fun getTxByHash(hash: String) = getTxByHashFromCache(hash)

    fun getTransactionJson(txnHash: String) = getTxByHash(txnHash)?.second?.let { protoPrinter.print(it) }
        ?: throw ResourceNotFoundException("Invalid transaction hash: '$txnHash'")

    fun getTransactionByHash(hash: String) = getTxByHash(hash)?.let { hydrateTxDetails(it.first.value, it.second) }
        ?: throw ResourceNotFoundException("Invalid transaction hash: '$hash'")

    private fun hydrateTxDetails(txId: Int, tx: ServiceOuterClass.GetTxResponse) = transaction {
        TxDetails(
            txHash = tx.txResponse.txhash,
            height = tx.txResponse.height.toInt(),
            gas = Gas(
                tx.txResponse.gasUsed.toInt(),
                tx.txResponse.gasWanted.toInt(),
                tx.tx.authInfo.fee.gasLimit.toBigInteger(),
                tx.tx.authInfo.fee.getMinGasFee()
            ),
            time = asyncCache.getBlock(tx.txResponse.height.toInt()).block.header.time.formattedString(),
            status = if (tx.txResponse.code > 0) "failed" else "success",
            errorCode = tx.txResponse.code,
            codespace = tx.txResponse.codespace,
            errorLog = if (tx.txResponse.code > 0) tx.txResponse.rawLog else null,
            fee = tx.tx.authInfo.fee.amountList.toProtoCoin().let { coin -> CoinStr(coin.amount, coin.denom) },
            signers = TxCacheRecord.findSigsByHash(tx.txResponse.txhash).toSigObj(props.provAccPrefix()),
            memo = tx.tx.body.memo,
            monikers = getMonikers(EntityID(txId, TxCacheTable))
        )
    }

    fun getTxMsgsPaginated(hash: String, msgType: String?, page: Int, count: Int) = transaction {
        val msgTypes = if (msgType != null) listOf(msgType) else listOf()
        val msgTypeIds = transaction { TxMessageTypeRecord.findByType(msgTypes).map { it.id.value } }.toList()

        TxCacheRecord.findByHash(hash)?.id?.value?.let { id ->
            val msgs = TxMessageRecord.findByHashIdPaginated(id, msgTypeIds, count, page.toOffset(count))
                .mapToTxMessages()
            val total = TxMessageRecord.getCountByHashId(id, msgTypeIds)
            PagedResults(total.pageCountOfResults(count), msgs, total)
        } ?: throw ResourceNotFoundException("Invalid transaction hash: '$hash'")
    }

    fun getTxHistoryByQuery(fromDate: DateTime, toDate: DateTime, granularity: DateTruncGranularity?) =
        BlockCacheRecord.getTxCountsForParams(fromDate, toDate, (granularity ?: DateTruncGranularity.DAY).name)

    private fun getMonikers(txId: EntityID<Int>): Map<String, String> {
        val monikers =
            TxAddressJoinRecord.findValidatorsByTxHash(txId).associate { v -> v.operatorAddress to v.moniker }
        val moduleNames =
            TxAddressJoinRecord.findAccountsByTxHash(txId)
                .filter { it.type == "ModuleAccount" }
                .associate { a -> a.accountAddress to a.data!!.getModuleAccName()!! }

        return monikers + moduleNames
    }
}

fun List<TxMessageTypeRecord>.mapToRes() =
    this.map { TxType(it.category ?: it.module, it.type) }.sortedWith(compareBy(TxType::module, TxType::type))
