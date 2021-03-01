package io.provenance.explorer.service

import cosmos.base.tendermint.v1beta1.Query
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockIndexRecord
import io.provenance.explorer.domain.entities.updateHitCount
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.models.explorer.TxHistory
import io.provenance.explorer.grpc.v1.BlockGrpcClient
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service

import java.math.BigDecimal


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
            ?.let { addBlockToCache(it.block.height(), it.block.data.txsCount, it.block.header.time.toDateTime(), it) }

    fun getBlockByHeightFromCache(blockHeight: Int) = transaction {
        BlockCacheRecord.findById(blockHeight)?.also {
            BlockCacheRecord.updateHitCount(blockHeight)
        }?.block
    }

    fun getChainIdString() =
        if (chainId.isEmpty()) getBlock(getLatestBlockHeightIndex())!!.block.header.chainId.also { this.chainId = it }
        else this.chainId

    fun addBlockToCache(
        blockHeight: Int,
        transactionCount: Int,
        timestamp: DateTime,
        blockMeta: Query.GetBlockByHeightResponse
    ) = BlockCacheRecord.insertIgnore(blockHeight, transactionCount, timestamp, blockMeta)

    fun updateBlockMaxHeightIndex(maxHeightRead: Int) = BlockIndexRecord.save(maxHeightRead, null)

    fun updateBlockMinHeightIndex(minHeightRead: Int) = BlockIndexRecord.save(null, minHeightRead)

    fun getHistoricalDaysBetweenHeights(maxHeightRead: Int, minHeightRead: Int) = transaction {
        val connection = TransactionManager.current().connection
        val query = "SELECT date_trunc('day', block_timestamp) " +
            "FROM block_cache WHERE height <= ? and height >= ? " +
            " GROUP BY 1 ORDER BY 1 DESC"
        val statement = connection.prepareStatement(query)
        statement.setObject(1, maxHeightRead)
        statement.setObject(2, minHeightRead)
        val resultSet = statement.executeQuery()
        val days = mutableListOf<DateTime>()
        while (resultSet.next()) { days.add(DateTime(resultSet.getDate(1))) }
        days
    }

    fun getTransactionCountsForDates(startDate: String, endDate: String, granularity: String) = transaction {
        val connection = TransactionManager.current().connection
        val query = """select date_trunc(?, block_timestamp), sum(tx_count) from block_cache 
            |where block_timestamp >= ?::timestamp and block_timestamp <=?::timestamp GROUP BY 1 ORDER BY 1 DESC""".trimMargin()
        val statement = connection.prepareStatement(query)
        statement.setObject(1, if (granularity.contains(granularity)) granularity else "day")
        statement.setObject(2, startDate)
        statement.setObject(3, endDate)
        val resultSet = statement.executeQuery()
        val metrics = mutableListOf<TxHistory>()
        while (resultSet.next()) {
            metrics.add(TxHistory(resultSet.getString(1), resultSet.getInt(2)))
        }
        metrics
    }

    fun getLatestBlockCreationIntervals(limit: Int) = transaction {
        val connection = TransactionManager.current().connection
        val query = "SELECT  height, " +
            "extract(epoch from LAG(block_timestamp) OVER(order by height desc) ) - " +
            "extract(epoch from block_timestamp) as block_creation_time " +
            "FROM block_cache limit ?"
        val statement = connection.prepareStatement(query)
        statement.setInt(1, limit)
        val resultSet = statement.executeQuery()
        val results = mutableListOf<Pair<Int, BigDecimal?>>()
        while (resultSet.next()) {
            results.add(Pair(resultSet.getInt(1), resultSet.getBigDecimal(2)))
        }
        results
    }
}
