package io.provenance.explorer.service

import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockIndexRecord
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.models.explorer.BlockUpdate
import io.provenance.explorer.grpc.v1.BlockGrpcClient
import org.springframework.stereotype.Service

@Service
class BlockService(private val blockClient: BlockGrpcClient) {
    protected val logger = logger(BlockService::class)

    fun getMaxBlockCacheHeight() = BlockCacheRecord.getMaxBlockHeight()

    fun getBlockIndexFromCache() = BlockIndexRecord.getIndex()

    fun getLatestBlockHeightIndex(): Int = getBlockIndexFromCache()!!.maxHeightRead!!

    fun getBlockAtHeightFromChain(height: Int) = try {
        blockClient.getBlockAtHeightFromFigment(height)
    } catch (e: Exception) {
        logger.error(e.message)
        blockClient.getBlockAtHeight(height)
    }

    fun getLatestBlockHeight(): Int = blockClient.getLatestBlock().block.height()

    fun saveBlock(blockUpdate: BlockUpdate) = BlockCacheRecord.insertToProcedure(blockUpdate)

    fun updateBlockMaxHeightIndex(maxHeightRead: Int) = BlockIndexRecord.save(maxHeightRead, null)

    fun updateBlockMinHeightIndex(minHeightRead: Int) = BlockIndexRecord.save(null, minHeightRead)
}

fun Int.getBlock() = BlockCacheRecord.findById(this)!!
