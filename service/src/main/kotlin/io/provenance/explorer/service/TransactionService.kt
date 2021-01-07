package io.provenance.explorer.service

import io.provenance.core.extensions.logger
import io.provenance.explorer.client.PbClient
import io.provenance.explorer.client.TendermintClient
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.PbTxSearchResponse
import org.springframework.stereotype.Service
import java.lang.Exception
import kotlin.system.measureTimeMillis

@Service
class TransactionService(private val explorerProperties: ExplorerProperties,
                         private val cacheService: CacheService,
                         private val pbClient: PbClient,
                         private val tendermintClient: TendermintClient) {

    protected val logger = logger(TransactionService::class)

    fun getBlocksByHeights(maxHeight: Int, minHeight: Int, page: Int, count: Int) = pbClient.getTxsByHeights(maxHeight, minHeight, page, count)

    fun getTxByHash(hash: String) = let {
        var tx = cacheService.getTransactionByHash(hash)
        if (tx == null) {
            logger.info("cache miss for transaction hash $hash")
            tx = pbClient.getTx(hash)
            cacheService.addTransactionToCache(tx)
        }
        tx
    }

    fun getTransactionsAtHeight(height: Int) = cacheService.getTransactionsAtHeight(height)

    fun addTransactionsToCache(blockHeight: Int, expectedNumTxs: Int) =
            if (cacheService.transactionCountForHeight(blockHeight) == expectedNumTxs)
                logger.info("Cache hit for transaction at height $blockHeight with $expectedNumTxs transactions")
            else {
                logger.info("Searching for $expectedNumTxs transactions at height $blockHeight")
                tryAddTxs(blockHeight, expectedNumTxs)
            }

    fun tryAddTxs(blockHeight: Int, txCount: Int) = try {
        var searchResult: PbTxSearchResponse? = null
        val elapsedTime = measureTimeMillis {
            //endpoint handles much faster when called with exact number of transactions.
            searchResult = getBlocksByHeights(blockHeight, blockHeight, 1, txCount)
        }
        logger.info("Search for transaction by height at $blockHeight took $elapsedTime")
        searchResult!!.txs.forEach {
            cacheService.addTransactionToCache(it)
        }
    } catch (e: Exception) {
        logger.error("Failed to retrieve transactions at block: $blockHeight", e)
    }
}