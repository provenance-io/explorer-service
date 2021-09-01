package io.provenance.explorer.service

import cosmos.base.tendermint.v1beta1.Query
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockIndexRecord
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.grpc.v1.BlockGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service

@Service
class BlockService(private val blockClient: BlockGrpcClient) {
    protected val logger = logger(BlockService::class)

    fun getMaxBlockCacheHeight() = BlockCacheRecord.getMaxBlockHeight()

    fun getBlockIndexFromCache() = BlockIndexRecord.getIndex()

    fun getLatestBlockHeightIndex(): Int = getBlockIndexFromCache()!!.maxHeightRead!!

    fun getBlockAtHeightFromChain(height: Int) = blockClient.getBlockAtHeight(height)

    fun getLatestBlockHeight(): Int = blockClient.getLatestBlock().block.height()

    fun addBlockToCache(
        blockHeight: Int,
        transactionCount: Int,
        timestamp: DateTime,
        blockMeta: Query.GetBlockByHeightResponse
    ) = transaction { BlockCacheRecord.insertIgnore(blockHeight, transactionCount, timestamp, blockMeta) }

    fun updateBlockMaxHeightIndex(maxHeightRead: Int) = BlockIndexRecord.save(maxHeightRead, null)

    fun updateBlockMinHeightIndex(minHeightRead: Int) = BlockIndexRecord.save(null, minHeightRead)
}
