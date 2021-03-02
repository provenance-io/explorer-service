package io.provenance.explorer.service

import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.TransactionCacheRecord
import io.provenance.explorer.domain.entities.updateHitCount
import io.provenance.explorer.domain.models.explorer.TxFromCache
import io.provenance.explorer.grpc.v1.TransactionGrpcClient
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class TransactionService(
    private val txClient: TransactionGrpcClient,
    private val blockService: BlockService
) {

    protected val logger = logger(TransactionService::class)

    fun getTxByHashFromCache(hash: String) = transaction {
        TransactionCacheRecord.findById(hash)?.also {
            TransactionCacheRecord.updateHitCount(hash)
        }?.txV2
    }

    fun txCount() = transaction { TransactionCacheRecord.all().count() }

    fun txCountForHeight(blockHeight: Int) = transaction { TransactionCacheRecord.findByHeight(blockHeight).count() }

    fun getTxs(count: Int, offset: Int) = transaction {
        TransactionCacheRecord.getAllWithOffset(SortOrder.DESC, count, offset)
            .map { TxFromCache(it.txV2, it.txType, it.txTimestamp) }
    }

    fun getBlocksByHeights(height: Int) = txClient.getTxsByHeight(height)

    fun getTxByHash(hash: String) = getTxByHashFromCache(hash) ?: txClient.getTxByHash(hash).addTxToCache()

    fun getTxsAtHeight(height: Int) = transaction { TransactionCacheRecord.findByHeight(height).map { it.txV2 } }

    fun addTxsToCache(blockHeight: Int, expectedNumTxs: Int) =
        if (txCountForHeight(blockHeight) == expectedNumTxs)
            logger.info("Cache hit for transaction at height $blockHeight with $expectedNumTxs transactions")
        else {
            logger.info("Searching for $expectedNumTxs transactions at height $blockHeight")
            tryAddTxs(blockHeight)
        }

    fun tryAddTxs(blockHeight: Int) = try {
        getBlocksByHeights(blockHeight).txResponsesList.forEach { txClient.getTxByHash(it.txhash).addTxToCache() }
    } catch (e: Exception) {
        logger.error("Failed to retrieve transactions at block: $blockHeight", e)
    }

    fun ServiceOuterClass.GetTxResponse.addTxToCache() =
        TransactionCacheRecord.insertIgnore(
            this,
            blockService.getBlock(this.txResponse.height.toInt())!!.block.header.time
        ).let { this }
}
