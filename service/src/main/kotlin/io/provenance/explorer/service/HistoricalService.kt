package io.provenance.explorer.service

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.extensions.day
import io.provenance.explorer.domain.extensions.height
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class HistoricalService(
    private val explorerProperties: ExplorerProperties,
    private val cacheService: CacheService,
    private val blockService: BlockService,
    private val transactionService: TransactionService
) {

    protected val logger = logger(HistoricalService::class)

    fun updateCache() {
        val index = getBlockIndex()
        val startHeight = blockService.getLatestBlockHeight()
        var indexHeight = startHeight
        if (startCollectingHistoricalBlocks(index) || continueCollectingHistoricalBlocks(index!!.first!!, index.second!!)) {
            val endDate = getEndDate()
            var shouldContinue = true
            if (index?.first == null)
                cacheService.updateBlockMaxHeightIndex(startHeight)
            indexHeight = index?.second?.minus(1) ?: indexHeight
            while (shouldContinue && indexHeight >= 0) {
                blockService.getBlockchain(indexHeight).result.blockMetas
                    .forEach { blockMeta ->
                        if (endDate >= DateTime(blockMeta.day())) {
                            shouldContinue = false
                            return@forEach
                        }
                        cacheService.addBlockToCache(blockMeta.height(), blockMeta.numTxs.toInt(), DateTime.parse(blockMeta.header.time), blockMeta)
                        if (blockMeta.numTxs.toInt() > 0) {
                            transactionService.addTransactionsToCache(blockMeta.height(), blockMeta.numTxs.toInt())
                        }
                        indexHeight = blockMeta.height() - 1
                    }
                cacheService.updateBlockMinHeightIndex(indexHeight + 1)
            }
        } else {
            while (indexHeight > index.first!!) {
                blockService.getBlockchain(indexHeight).result.blockMetas
                    .forEach { blockMeta ->
                        if (blockMeta.height() <= index.first!!)
                            return@forEach
                        cacheService.addBlockToCache(blockMeta.height(), blockMeta.numTxs.toInt(), DateTime.parse(blockMeta.header.time), blockMeta)
                        indexHeight = blockMeta.height() - 1
                        if (blockMeta.numTxs.toInt() > 0) {
                            transactionService.addTransactionsToCache(blockMeta.height(), blockMeta.numTxs.toInt())
                        }
                    }
            }
            cacheService.updateBlockMaxHeightIndex(startHeight)
        }
    }

    fun getBlockIndex() = cacheService.getBlockIndex()?.let {
        Pair<Int?, Int?>(it.maxHeightRead, it.minHeightRead)
    }

    fun startCollectingHistoricalBlocks(blockIndex: Pair<Int?, Int?>?) = blockIndex?.second == null || blockIndex.first == null

    fun continueCollectingHistoricalBlocks(maxRead: Int, minRead: Int): Boolean {
        val historicalDays = cacheService.getHistoricalDaysBetweenHeights(maxRead, minRead)
        return historicalDays.size < explorerProperties.initialHistoricalDays() && minRead != 0
    }

    fun getEndDate() = LocalDate().toDateTimeAtStartOfDay().minusDays(explorerProperties.initialHistoricalDays() + 1)

    @Scheduled(initialDelay = 0L, fixedDelay = 1000L)
    fun updateLatestBlockHeightJob() = updateCache()

}
