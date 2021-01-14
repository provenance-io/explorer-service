package io.provenance.explorer.service

import io.provenance.explorer.client.PbClient
import io.provenance.explorer.domain.core.logger
import org.springframework.stereotype.Service

@Service
class TransactionService(
    private val cacheService: CacheService,
    private val pbClient: PbClient
) {

    protected val logger = logger(TransactionService::class)

    fun getBlocksByHeights(maxHeight: Int, minHeight: Int, page: Int, count: Int) =
        pbClient.getTxsByHeights(maxHeight, minHeight, page, count)

    fun getTxByHash(hash: String) = cacheService.getTransactionByHash(hash)
        ?: cacheService.addTransactionToCache(pbClient.getTx(hash))

    fun getTransactionsAtHeight(height: Int) = cacheService.getTransactionsAtHeight(height)

    fun addTransactionsToCache(blockHeight: Int, expectedNumTxs: Int) =
        if (cacheService.transactionCountForHeight(blockHeight) == expectedNumTxs)
            logger.info("Cache hit for transaction at height $blockHeight with $expectedNumTxs transactions")
        else {
            logger.info("Searching for $expectedNumTxs transactions at height $blockHeight")
            tryAddTxs(blockHeight, expectedNumTxs)
        }

    fun tryAddTxs(blockHeight: Int, txCount: Int) = try {
        //endpoint handles much faster when called with exact number of transactions.
        getBlocksByHeights(blockHeight, blockHeight, 1, txCount)
            .txs.forEach { cacheService.addTransactionToCache(it) }
    } catch (e: Exception) {
        logger.error("Failed to retrieve transactions at block: $blockHeight", e)
    }
}
