package io.provenance.explorer.service

import io.provenance.core.extensions.logger
import io.provenance.explorer.client.PbClient
import io.provenance.explorer.client.TendermintClient
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.BlockIndexTable
import io.provenance.explorer.domain.PbTxSearchResponse
import io.provenance.explorer.domain.day
import io.provenance.explorer.domain.height
import org.joda.time.DateTime
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.lang.Exception
import java.net.SocketTimeoutException
import kotlin.system.measureTimeMillis

@Service
class HistoricalService(private val explorerProperties: ExplorerProperties,
                        private val cacheService: CacheService,
                        private val blockService: BlockService,
                        private val transactionService: TransactionService
) {


    protected val logger = logger(ExplorerService::class)

    fun updateCache() {
        val index = cacheService.getBlockIndex()
        val startHeight = blockService.getLatestBlockHeight()
        var indexHeight = startHeight
        cacheService.updateBlockMaxHeightIndex(indexHeight)
        if (index == null || index[BlockIndexTable.minHeightRead] == null) {
            val days = mutableSetOf<String>()
            while (days.count() <= explorerProperties.initialHistoricalDays() || indexHeight < 0) {
                blockService.getBlockchain(indexHeight).result.blockMetas.forEach { blockMeta ->
                    cacheService.addBlockToCache(blockMeta.height(), blockMeta.numTxs.toInt(), DateTime.parse(blockMeta.header.time), blockMeta)
                    days.add(blockMeta.day())
                    if (blockMeta.numTxs.toInt() > 0) {
                        transactionService.addTransactionsToCache(blockMeta.height(), blockMeta.numTxs.toInt())
                    }
                    indexHeight = blockMeta.height() - 1
                }
                cacheService.updateBlockMinHeightIndex(indexHeight + 1)
            }
        } else {
            while (indexHeight > index[BlockIndexTable.maxHeightRead]) {
                blockService.getBlockchain(indexHeight).result.blockMetas.forEach { blockMeta ->
                    if (blockMeta.height() <= index[BlockIndexTable.maxHeightRead]) return@forEach
                    cacheService.addBlockToCache(blockMeta.height(), blockMeta.numTxs.toInt(), DateTime.parse(blockMeta.header.time), blockMeta)
                    indexHeight = blockMeta.height() - 1
                    if (blockMeta.numTxs.toInt() > 0) {
                        transactionService.addTransactionsToCache(blockMeta.height(), blockMeta.numTxs.toInt())
                    }
                }
            }
        }
    }

    @Scheduled(initialDelay = 0L, fixedDelay = 1000L)
    fun updateLatestBlockHeightJob() = updateCache()

}