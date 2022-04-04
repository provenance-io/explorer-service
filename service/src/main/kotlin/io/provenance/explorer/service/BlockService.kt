package io.provenance.explorer.service

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockIndexRecord
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.grpc.v1.BlockGrpcClient
import org.springframework.stereotype.Service

@Service
class BlockService(private val blockClient: BlockGrpcClient, private val props: ExplorerProperties) {
    protected val logger = logger(BlockService::class)

    fun getMaxBlockCacheHeight() = BlockCacheRecord.getMaxBlockHeight()

    fun getBlockIndexFromCache() = BlockIndexRecord.getIndex()

    fun getLatestBlockHeightIndex(): Int = getBlockIndexFromCache()!!.maxHeightRead!!

    fun getLatestBlockHeightIndexOrFromChain() = getBlockIndexFromCache()?.maxHeightRead ?: getLatestBlockHeight()

    fun getBlockAtHeightFromChain(height: Int) = try {
        if (props.mainnet.toBoolean())
            blockClient.getBlockAtHeightFromFigment(height)
        else blockClient.getBlockAtHeight(height)
    } catch (e: Exception) {
        logger.error(e.message)
        blockClient.getBlockAtHeight(height)
    }

    fun getLatestBlockHeight(): Int = blockClient.getLatestBlock().block.height()

    fun updateBlockMaxHeightIndex(maxHeightRead: Int) = BlockIndexRecord.save(maxHeightRead, null)

    fun updateBlockMinHeightIndex(minHeightRead: Int) = BlockIndexRecord.save(null, minHeightRead)
}

fun Int.getBlock() = BlockCacheRecord.findById(this)!!
