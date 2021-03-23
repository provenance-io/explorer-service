package io.provenance.explorer.service

import cosmos.base.tendermint.v1beta1.Query
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockIndexRecord
import io.provenance.explorer.domain.entities.updateHitCount
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.grpc.v1.BlockGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service


@Service
class BlockService(private val blockClient: BlockGrpcClient) {
    protected val logger = logger(BlockService::class)

    protected var chainId: String = ""

    fun getBlockIndexFromCache() = BlockIndexRecord.getIndex()

    fun getLatestBlockHeightIndex(): Int = getBlockIndexFromCache()!!.maxHeightRead!!

    fun getBlockAtHeightFromChain(height: Int) = blockClient.getBlockAtHeight(height)

    fun getLatestBlockHeight(): Int = blockClient.getLatestBlock().block.height()

    fun getBlock(blockHeight: Int) =
        getBlockByHeightFromCache(blockHeight) ?: getBlockAtHeightFromChain(blockHeight)
            .let { addBlockToCache(it.block.height(), it.block.data.txsCount, it.block.header.time.toDateTime(), it) }

    fun getBlockByHeightFromCache(blockHeight: Int) = transaction {
        BlockCacheRecord.findById(blockHeight)?.also {
            BlockCacheRecord.updateHitCount(blockHeight)
        }?.block
    }

    fun getChainIdString() =
        if (chainId.isEmpty()) getBlock(getLatestBlockHeightIndex()).block.header.chainId.also { this.chainId = it }
        else this.chainId

    fun addBlockToCache(
        blockHeight: Int,
        transactionCount: Int,
        timestamp: DateTime,
        blockMeta: Query.GetBlockByHeightResponse
    ) = transaction { BlockCacheRecord.insertIgnore(blockHeight, transactionCount, timestamp, blockMeta) }

    fun updateBlockMaxHeightIndex(maxHeightRead: Int) = BlockIndexRecord.save(maxHeightRead, null)

    fun updateBlockMinHeightIndex(minHeightRead: Int) = BlockIndexRecord.save(null, minHeightRead)
}
