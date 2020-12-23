package io.provenance.explorer.service

import io.provenance.core.extensions.logger
import io.provenance.explorer.client.PbClient
import io.provenance.explorer.client.TendermintClient
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.BlockIndexTable
import io.provenance.explorer.domain.height
import org.joda.time.DateTime
import org.springframework.stereotype.Service

@Service
class BlockService(private val explorerProperties: ExplorerProperties,
                   private val cacheService: CacheService,
                   private val pbClient: PbClient,
                   private val tendermintClient: TendermintClient
) {

    protected val logger = logger(BlockService::class)

    fun getLatestBlockHeightIndex(): Int = cacheService.getBlockIndex()!![BlockIndexTable.maxHeightRead]

    fun getBlockchain(maxHeight: Int) = tendermintClient.getBlockchain(maxHeight)

    fun getLatestBlockHeight(): Int = getStatus().syncInfo.latestBlockHeight.toInt()

    private fun getStatus() = tendermintClient.getStatus().result

    fun getBlock(blockHeight: Int) = let {
        var block = cacheService.getBlockByHeight(blockHeight)
        if (block == null) {
            logger.info("cache miss for block height $blockHeight")
            getBlockchain(blockHeight).result.blockMetas.filter {
                it.header.height.toInt() == blockHeight
            }.map {
                cacheService.addBlockToCache(it.height(), it.numTxs.toInt(), DateTime.parse(it.header.time), it)
                block
            }
        }
        block!!
    }

}