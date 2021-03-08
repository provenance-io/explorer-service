package io.provenance.explorer.service

import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.util.JsonFormat
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.TxAddressJoinRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.entities.updateHitCount
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.toSigObj
import io.provenance.explorer.domain.models.explorer.DenomAmount
import io.provenance.explorer.domain.models.explorer.MsgTypeSet
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.TxMessage
import io.provenance.explorer.domain.models.explorer.TxSummary
import io.provenance.explorer.domain.models.explorer.TxType
import io.provenance.explorer.grpc.v1.TransactionGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service

@Service
class TransactionService(
    private val txClient: TransactionGrpcClient,
    private val blockService: BlockService,
    private val protoPrinter: JsonFormat.Printer,
    private val props: ExplorerProperties
) {

    protected val logger = logger(TransactionService::class)

    fun getTxByHashFromCache(hash: String) = transaction {
        TxCacheRecord.findById(hash)?.also {
            TxCacheRecord.updateHitCount(hash)
        }?.txV2
    }

    fun txCountForHeight(blockHeight: Int) = transaction { TxCacheRecord.findByHeight(blockHeight).count() }

    fun getTxByHash(hash: String) = getTxByHashFromCache(hash) ?: txClient.getTxByHash(hash).addTxToCache()

    fun getTxsAtHeight(height: Int) = transaction { TxCacheRecord.findByHeight(height).map { it.txV2 } }

    fun addTxsToCache(blockHeight: Int, expectedNumTxs: Int) =
        if (txCountForHeight(blockHeight) == expectedNumTxs)
            logger.info("Cache hit for transaction at height $blockHeight with $expectedNumTxs transactions")
        else {
            logger.info("Searching for $expectedNumTxs transactions at height $blockHeight")
            tryAddTxs(blockHeight)
        }

    fun tryAddTxs(blockHeight: Int) = try {
        txClient.getTxsByHeight(blockHeight).txResponsesList
            .forEach { txClient.getTxByHash(it.txhash).addTxToCache() }
    } catch (e: Exception) {
        logger.error("Failed to retrieve transactions at block: $blockHeight", e)
    }

    fun ServiceOuterClass.GetTxResponse.addTxToCache() =
        TxCacheRecord.insertIgnore(
            this,
            blockService.getBlock(this.txResponse.height.toInt())!!.block.header.time
        ).let { this }

    fun getTxTypes(typeSet: MsgTypeSet?) = transaction {
        when (typeSet) {
            null -> TxMessageTypeRecord.all()
            else -> TxMessageTypeRecord.findByType(typeSet.types)
        }.map { TxType(it.category ?: it.module, it.type) }
    }

    fun getTxsByQuery(
        address: String?,
        module: MsgTypeSet?,
        msgType: String?,
        count: Int,
        page: Int,
        fromDate: DateTime?,
        toDate: DateTime?
    ) = transaction {
        val msgTypes = if (msgType != null) listOf(msgType) else module?.types ?: listOf()

        val query = TxMessageRecord.findByQueryParams(address, msgTypes, count, page.toOffset(count), fromDate, toDate)
        query.first.map {
            TxSummary(
                it.id.value,
                it.height,
                it.txMessages.map { msg ->
                    TxMessage(
                        msg.txMessageType.type,
                        OBJECT_MAPPER.readValue(protoPrinter.print(msg.txMessage), ObjectNode::class.java)
                            .let { node ->
                                node.remove("@type")
                                node
                            }) },
                TxAddressJoinRecord.findValidatorsByTxHash(it.id).map { v -> v.operatorAddress to v.moniker }.toMap(),
                it.txTimestamp.toString(),
                DenomAmount(
                    it.txV2.tx.authInfo.fee.amountList[0].denom,
                    it.txV2.tx.authInfo.fee.amountList[0].amount.toBigInteger()
                ),
                TxCacheRecord.findSigsByHash(it.id.value).toSigObj(props.provAccPrefix()),
                if (it.errorCode == 0) "success" else "failed"
            )
        }.let { PagedResults(query.second.pageCountOfResults(count), it) }


    }
}
