package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
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
import io.provenance.explorer.domain.extensions.toHash
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.toSigObj
import io.provenance.explorer.domain.models.explorer.CoinStr
import io.provenance.explorer.domain.models.explorer.DateTruncGranularity
import io.provenance.explorer.domain.models.explorer.Gas
import io.provenance.explorer.domain.models.explorer.MsgTypeSet
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.TxDetails
import io.provenance.explorer.domain.models.explorer.TxMessage
import io.provenance.explorer.domain.models.explorer.TxQueryParams
import io.provenance.explorer.domain.models.explorer.TxStatus
import io.provenance.explorer.domain.models.explorer.TxSummary
import io.provenance.explorer.domain.models.explorer.TxType
import io.provenance.explorer.grpc.v1.TransactionGrpcClient
import io.provenance.explorer.service.async.AsyncCaching
import io.provenance.explorer.service.async.getAddressType
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service

@Service
class TransactionService(
    private val protoPrinter: JsonFormat.Printer,
    private val props: ExplorerProperties,
    private val asyncCache: AsyncCaching
) {

    protected val logger = logger(TransactionService::class)

    private fun getTxByHashFromCache(hash: String) =
        transaction { TxCacheRecord.findByHash(hash)?.let { Pair(it.id, it.txV2) } }

    fun getTxTypes(typeSet: MsgTypeSet?) = transaction {
        when (typeSet) {
            null -> TxMessageTypeRecord.all()
            else -> TxMessageTypeRecord.findByType(typeSet.types)
        }.map { TxType(it.category ?: it.module, it.type) }
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
        toDate: DateTime?
    ) = transaction {
            val msgTypes = if (msgType != null) listOf(msgType) else module?.types ?: listOf()
            val msgTypeIds = TxMessageTypeRecord.findByType(msgTypes).map { it.id.value }.toList()
            val addr = address?.getAddressType(props)
            val markerId = if (denom != null) MarkerCacheRecord.findByDenom(denom)?.id?.value else null

            val params =
                TxQueryParams(addr?.second, addr?.first, address, markerId, denom, msgTypeIds, txHeight, txStatus,
                    count, page.toOffset(count), fromDate, toDate)

            val total = TxCacheRecord.findByQueryParamsForCount(params)
            TxCacheRecord.findByQueryForResults(params).map {
                TxSummary(
                    it.hash,
                    it.height,
                    it.txMessages.mapToTxMessages(),
                    TxAddressJoinRecord.findValidatorsByTxHash(it.id)
                        .map { v -> v.operatorAddress to v.moniker }.toMap(),
                    it.txTimestamp.toString(),
                    it.txV2.tx.authInfo.fee.amountList.first().amount
                        .toHash(it.txV2.tx.authInfo.fee.amountList.first().denom)
                        .let { coin -> CoinStr(coin.first, coin.second, it.txV2.tx.authInfo.fee.amountList.first().denom) },
                    TxCacheRecord.findSigsByHash(it.hash).toSigObj(props.provAccPrefix()),
                    if (it.errorCode == null) "success" else "failed"
                )
            }.let { PagedResults(total.pageCountOfResults(count), it) }
        }

    fun SizedIterable<TxMessageRecord>.mapToTxMessages() =
        this.map { msg -> TxMessage(msg.txMessageType.type, msg.txMessage.toObjectNode(protoPrinter)) }


    private fun getTxByHash(hash: String) = getTxByHashFromCache(hash)

    fun getTransactionJson(txnHash: String) = protoPrinter.print(getTxByHash(txnHash)?.second)

    fun getTransactionByHash(hash: String) = getTxByHash(hash)?.let { hydrateTxDetails(it.first.value, it.second) }

    private fun hydrateTxDetails(txId: Int, tx: ServiceOuterClass.GetTxResponse) = transaction {
        TxDetails(
            txHash = tx.txResponse.txhash,
            height = tx.txResponse.height.toInt(),
            gas = Gas(
                tx.txResponse.gasUsed.toInt(),
                tx.txResponse.gasWanted.toInt(),
                tx.tx.authInfo.fee.gasLimit.toInt(),
                tx.tx.authInfo.fee.getMinGasFee()),
            time = asyncCache.getBlock(tx.txResponse.height.toInt()).block.header.time.formattedString(),
            status = if (tx.txResponse.code > 0) "failed" else "success",
            errorCode = tx.txResponse.code,
            codespace = tx.txResponse.codespace,
            errorLog = if (tx.txResponse.code > 0) tx.txResponse.rawLog else null,
            fee = tx.tx.authInfo.fee.amountList.first().amount
                .toHash(tx.tx.authInfo.fee.amountList.first().denom)
                .let { CoinStr(it.first, it.second, tx.tx.authInfo.fee.amountList.first().denom) },
            signers = TxCacheRecord.findSigsByHash(tx.txResponse.txhash).toSigObj(props.provAccPrefix()),
            memo = tx.tx.body.memo,
            msg = TxMessageRecord.findByHashId(txId).mapToTxMessages(),
            monikers = TxAddressJoinRecord.findValidatorsByTxHash(EntityID(txId, TxCacheTable))
                .map { v -> v.operatorAddress to v.moniker }.toMap()
        )
    }

    fun getTxHistoryByQuery(fromDate: DateTime, toDate: DateTime, granularity: DateTruncGranularity?) =
        BlockCacheRecord.getTxCountsForParams(fromDate, toDate, (granularity ?: DateTruncGranularity.DAY).name)
}
