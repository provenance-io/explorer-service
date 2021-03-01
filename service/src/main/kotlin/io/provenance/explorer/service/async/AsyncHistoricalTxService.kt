package io.provenance.explorer.service.async

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.service.BlockService
import io.provenance.explorer.service.TransactionService
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import tendermint.types.BlockOuterClass

@Service
class AsyncHistoricalTxService(
    private val explorerProperties: ExplorerProperties,
    private val blockService: BlockService,
    private val transactionService: TransactionService
) {

    protected val logger = logger(AsyncHistoricalTxService::class)

    @Scheduled(initialDelay = 0L, fixedDelay = 1000L)
    fun updateLatestBlockHeightJob() = updateCache()

    fun updateCache() {
        val index = getBlockIndex()
        val startHeight = blockService.getLatestBlockHeight()
        var indexHeight = startHeight
        if (startCollectingHistoricalBlocks(index) || continueCollectingHistoricalBlocks(
                index!!.first!!,
                index.second!!
            )
        ) {
            val endDate = getEndDate()
            var shouldContinue = true
            if (index?.first == null)
                blockService.updateBlockMaxHeightIndex(startHeight)
            indexHeight = index?.second?.minus(1) ?: indexHeight
            while (shouldContinue && indexHeight > 0) {
                blockService.getBlockAtHeightFromChain(indexHeight).let {
                    if (endDate >= it.block.day()) {
                        shouldContinue = false
                        return
                    }
                    blockService.addBlockToCache(
                        it.block.height(),
                        it.block.data.txsCount,
                        it.block.header.time.toDateTime(),
                        it)
                    if (it.block.data.txsCount > 0) {
                        transactionService.addTxsToCache(it.block.height(), it.block.data.txsCount)
                    }
                    indexHeight = it.block.height() - 1
                }
                blockService.updateBlockMinHeightIndex(indexHeight + 1)
            }
        } else {
            while (indexHeight > index.first!!) {
                blockService.getBlockAtHeightFromChain(indexHeight).let {
                    blockService.addBlockToCache(
                        it.block.height(), it.block.data.txsCount,
                        it.block.header.time.toDateTime(), it)
                    indexHeight = it.block.height() - 1
                    if (it.block.data.txsCount > 0) {
                        transactionService.addTxsToCache(it.block.height(), it.block.data.txsCount)
                    }
                }
            }
            blockService.updateBlockMaxHeightIndex(startHeight)
        }
    }

    fun getBlockIndex() = blockService.getBlockIndexFromCache()?.let {
        Pair<Int?, Int?>(it.maxHeightRead, it.minHeightRead)
    }

    fun startCollectingHistoricalBlocks(blockIndex: Pair<Int?, Int?>?) =
        blockIndex?.second == null || blockIndex.first == null

    fun continueCollectingHistoricalBlocks(maxRead: Int, minRead: Int): Boolean {
        val historicalDays = blockService.getHistoricalDaysBetweenHeights(maxRead, minRead)
        return historicalDays.size < explorerProperties.initialHistoricalDays() && minRead > 1
    }

    fun getEndDate() = LocalDate().toDateTimeAtStartOfDay().minusDays(explorerProperties.initialHistoricalDays() + 1)

    fun BlockOuterClass.Block.day() = this.header.time.toDateTime()

}
