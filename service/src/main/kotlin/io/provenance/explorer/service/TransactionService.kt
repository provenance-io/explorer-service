package io.provenance.explorer.service

import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.util.JsonFormat
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.TxAddressJoinRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxCacheTable
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.entities.updateHitCount
import io.provenance.explorer.domain.extensions.formattedString
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.toSigObj
import io.provenance.explorer.domain.models.explorer.DateTruncGranularity
import io.provenance.explorer.domain.models.explorer.DenomAmount
import io.provenance.explorer.domain.models.explorer.MsgTypeSet
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.TxDetails
import io.provenance.explorer.domain.models.explorer.TxMessage
import io.provenance.explorer.domain.models.explorer.TxStatus
import io.provenance.explorer.domain.models.explorer.TxSummary
import io.provenance.explorer.domain.models.explorer.TxType
import io.provenance.explorer.grpc.v1.TransactionGrpcClient
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SizedIterable
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

    fun addTxsToCache(blockHeight: Int, expectedNumTxs: Int) =
        if (txCountForHeight(blockHeight).toInt() == expectedNumTxs)
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
            blockService.getBlock(this.txResponse.height.toInt()).block.header.time
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
        txHeight: Int?,
        txStatus: TxStatus?,
        count: Int,
        page: Int,
        fromDate: DateTime?,
        toDate: DateTime?
    ) = transaction {
        val msgTypes = if (msgType != null) listOf(msgType) else module?.types ?: listOf()

        val query = TxMessageRecord.findByQueryParams(
                address, msgTypes, txHeight, txStatus, count, page.toOffset(count), fromDate, toDate)
        query.first.map {
            TxSummary(
                it.hash,
                it.height,
                it.txMessages.mapToTxMessages(),
                TxAddressJoinRecord.findValidatorsByTxHash(it.id).map { v -> v.operatorAddress to v.moniker }.toMap(),
                it.txTimestamp.toString(),
                DenomAmount(
                    it.txV2.tx.authInfo.fee.amountList[0].denom,
                    it.txV2.tx.authInfo.fee.amountList[0].amount.toBigInteger()
                ),
                TxCacheRecord.findSigsByHash(it.hash).toSigObj(props.provAccPrefix()),
                if (it.errorCode == 0) "success" else "failed"
            )
        }.let { PagedResults(query.second.pageCountOfResults(count), it) }
    }

    fun SizedIterable<TxMessageRecord>.mapToTxMessages() = this.map { msg ->
        TxMessage(
            msg.txMessageType.type,
            OBJECT_MAPPER.readValue(protoPrinter.print(msg.txMessage), ObjectNode::class.java)
                .let { node ->
                    node.remove("@type")
                    node
                }) }

    fun getTxByHash(hash: String) = getTxByHashFromCache(hash) ?: txClient.getTxByHash(hash).addTxToCache()

    fun getTransactionJson(txnHash: String) = protoPrinter.print(getTxByHash(txnHash))

    fun getTransactionByHash(hash: String) = hydrateTxDetails(getTxByHash(hash))

    private fun hydrateTxDetails(tx: ServiceOuterClass.GetTxResponse) = transaction {
        TxDetails(
            txHash = tx.txResponse.txhash,
            height = tx.txResponse.height.toInt(),
            gasUsed = tx.txResponse.gasUsed.toInt(),
            gasWanted = tx.txResponse.gasWanted.toInt(),
            gasLimit = tx.tx.authInfo.fee.gasLimit.toInt(),
            gasPrice = props.minGasPrice(),
            time = blockService.getBlock(tx.txResponse.height.toInt()).block.header.time.formattedString(),
            status = if (tx.txResponse.code > 0) "failed" else "success",
            errorCode = tx.txResponse.code,
            codespace = tx.txResponse.codespace,
            errorLog = if (tx.txResponse.code > 0) tx.txResponse.rawLog else null,
            fee = DenomAmount(tx.tx.authInfo.fee.amountList[0].denom,
                tx.tx.authInfo.fee.amountList[0].amount.toBigInteger()),
            signers = TxCacheRecord.findSigsByHash(tx.txResponse.txhash).toSigObj(props.provAccPrefix()),
            memo = tx.tx.body.memo,
            msg = TxMessageRecord.findByHash(tx.txResponse.txhash).mapToTxMessages(),
            monikers = TxAddressJoinRecord.findValidatorsByTxHash(EntityID(tx.txResponse.txhash, TxCacheTable))
                .map { v -> v.operatorAddress to v.moniker }.toMap()
        )
    }

    fun getTxHistoryByQuery(fromDate: DateTime, toDate: DateTime, granularity: DateTruncGranularity?) =
        BlockCacheRecord.getTxCountsForParams(fromDate, toDate, (granularity ?: DateTruncGranularity.DAY).name)
}
