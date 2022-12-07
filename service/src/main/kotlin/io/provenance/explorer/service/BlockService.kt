package io.provenance.explorer.service

import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockIndexRecord
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.grpc.v1.BlockGrpcClient
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class BlockService(private val blockClient: BlockGrpcClient) {
    protected val logger = logger(BlockService::class)

    fun getMaxBlockCacheHeight() = BlockCacheRecord.getMaxBlockHeight()

    fun getBlockIndexFromCache() = BlockIndexRecord.getIndex()

    fun getLatestBlockHeightIndex(): Int = getBlockIndexFromCache()!!.maxHeightRead!!

    fun getLatestBlockHeightIndexOrFromChain() = getBlockIndexFromCache()?.maxHeightRead ?: getLatestBlockHeight()

    fun getBlockAtHeightFromChain(height: Int) =
        runBlocking { blockClient.getBlockAtHeight(height) } ?: getBlockAtHeight(height)?.block

    fun getBlockAtHeight(height: Int) = transaction { BlockCacheRecord.findById(height) }

    fun getLatestBlockHeight(): Int =
        runBlocking { blockClient.getLatestBlock()?.block?.height() } ?: getMaxBlockCacheHeight()

    fun updateBlockMaxHeightIndex(maxHeightRead: Int) = BlockIndexRecord.save(maxHeightRead, null)

    fun updateBlockMinHeightIndex(minHeightRead: Int) = BlockIndexRecord.save(null, minHeightRead)
}

fun Int.getBlock() = BlockCacheRecord.findById(this)!!
