package io.provenance.explorer.service

import com.fasterxml.jackson.databind.JsonNode
import io.provenance.core.extensions.logger
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.*
import io.provenance.explorer.domain.SpotlightCacheTable.lastHit
import io.provenance.explorer.domain.SpotlightCacheTable.spotlight
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class CacheService(private val explorerProperties: ExplorerProperties) {

    protected val logger = logger(CacheService::class)

    fun getBlockByHeight(blockHeight: Int) = transaction {
        var blockMeta: BlockMeta? = null
        var block = BlockCacheTable.select { (BlockCacheTable.height eq blockHeight) }.firstOrNull()?.let {
            it
        }
        if (block != null) {
            BlockCacheTable.update({ BlockCacheTable.height eq blockHeight }) {
                it[hitCount] = block[hitCount] + 1
                it[lastHit] = DateTime.now()
            }
            blockMeta = block[BlockCacheTable.block]
        }
        blockMeta
    }

    fun addBlockToCache(blockHeight: Int, transactionCount: Int, timestamp: DateTime, blockMe: BlockMeta) = transaction {
        if (shouldCacheBlock(blockHeight, blockMe)) BlockCacheTable.insertIgnore {
            it[height] = blockHeight
            it[this.block] = blockMe
            it[txCount] = transactionCount
            it[blockTimestamp] = timestamp
            it[hitCount] = 0
            it[lastHit] = DateTime.now()
        }
    }

    fun shouldCacheBlock(blockHeight: Int, blockMeta: BlockMeta) = blockMeta.height() == blockHeight

    fun getValidatorsByHeight(blockHeight: Int) = transaction {
        var jsonNode: JsonNode? = null
        var validators = ValidatorsCacheTable.select { (ValidatorsCacheTable.height eq blockHeight) }.firstOrNull()?.let {
            it
        }
        if (validators != null) {
            ValidatorsCacheTable.update({ ValidatorsCacheTable.height eq blockHeight }) {
                it[hitCount] = validators[hitCount] + 1
                it[lastHit] = DateTime.now()
            }
            jsonNode = validators[ValidatorsCacheTable.validators]
        }
        jsonNode
    }

    fun addValidatorsToCache(blockHeight: Int, json: JsonNode) = transaction {
        ValidatorsCacheTable.insertIgnore {
            it[height] = blockHeight
            it[validators] = json
            it[hitCount] = 0
            it[lastHit] = DateTime.now()
        }
    }

    fun getValidatorByHash(hash: String) = transaction {
        var jsonNode: JsonNode? = null
        var validator = ValidatorCacheTable.select { (ValidatorCacheTable.hash eq hash) }.firstOrNull()?.let {
            it
        }
        if (validator != null) {
            ValidatorCacheTable.update({ ValidatorCacheTable.hash eq hash }) {
                it[hitCount] = validator[hitCount] + 1
                it[lastHit] = DateTime.now()
            }
            jsonNode = validator[ValidatorCacheTable.validator]
        }
        jsonNode
    }

    fun addValidatorToCache(hash: String, json: JsonNode) = transaction {
        ValidatorCacheTable.insertIgnore {
            it[ValidatorCacheTable.hash] = hash
            it[validator] = json
            it[hitCount] = 0
            it[lastHit] = DateTime.now()
        }
    }

    fun getTransactionByHash(hash: String) = transaction {
        var tx = TransactionCacheTable.select { (TransactionCacheTable.hash eq hash) }.firstOrNull()?.let {
            it
        }
        if (tx != null) {
            TransactionCacheTable.update({ TransactionCacheTable.hash eq hash }) {
                it[hitCount] = tx[hitCount] + 1
                it[lastHit] = DateTime.now()
            }
        }
        if (tx == null) null else tx[TransactionCacheTable.tx]
    }

    fun addTransactionToCache(pbTransaction: PbTransaction) = transaction {
        TransactionCacheTable.insertIgnore {
            it[hash] = pbTransaction.txhash
            it[height] = pbTransaction.height.toInt()
            if (pbTransaction.code != null) it[errorCode] = pbTransaction.code
            if (pbTransaction.codespace != null) it[codespace] = pbTransaction.codespace
            it[txType] = if (pbTransaction.code != null) pbTransaction.type()!! else "ERROR"
            it[gasUsed] = pbTransaction.gasUsed.toInt()
            it[gasWanted] = pbTransaction.gasWanted.toInt()
            it[txTimestamp] = DateTime.parse(pbTransaction.timestamp)
            it[tx] = pbTransaction
            it[hitCount] = 0
            it[lastHit] = DateTime.now()
        }
    }

    fun transactionCount() = transaction {
        TransactionCacheTable.selectAll().count()
    }

    fun transactionCountForHeight(blockHeight: Int) = transaction {
        TransactionCacheTable.select { (TransactionCacheTable.height eq blockHeight) }.count()
    }

    fun getTransactionsAtHeight(blockHeight: Int) = transaction {
        TransactionCacheTable.select { (TransactionCacheTable.height eq blockHeight) }.map { it[TransactionCacheTable.tx] }
    }

    fun getTransactions(count: Int, offset: Int) = transaction {
        TransactionCacheTable.selectAll()
                .orderBy(TransactionCacheTable.height, SortOrder.DESC)
                .limit(count, offset)
                .map { it[TransactionCacheTable.tx] }
    }

    fun getBlockIndex() = transaction {
        BlockIndexTable.select { (BlockIndexTable.id eq 1) }.firstOrNull()
    }

    fun updateBlockMaxHeightIndex(maxHeightRead: Int) = updateBlockIndex(maxHeightRead, null)

    fun updateBlockMinHeightIndex(minHeightRead: Int) = updateBlockIndex(null, minHeightRead)

    private fun updateBlockIndex(maxHeightRead: Int?, minHeightRead: Int?) = transaction {
        val blockIndex = BlockIndexTable.select { (BlockIndexTable.id eq 1) }.firstOrNull()
        if (blockIndex == null) {
            BlockIndexTable.insert {
                it[id] = 1
                if (maxHeightRead != null) it[BlockIndexTable.maxHeightRead] = maxHeightRead
                if (minHeightRead != null) it[BlockIndexTable.minHeightRead] = minHeightRead
                it[lastUpdate] = DateTime.now()
            }
        } else {
            BlockIndexTable.update {
                if (maxHeightRead != null) it[BlockIndexTable.maxHeightRead] = maxHeightRead
                if (minHeightRead != null) it[BlockIndexTable.minHeightRead] = minHeightRead
                it[lastUpdate] = DateTime.now()
            }
        }
    }

    fun getTransactionCountsForDates(startDate: String, endDate: String, granularity: String) = transaction {
        val connection = TransactionManager.current().connection
        val query = "select date_trunc(?, block_timestamp), sum(tx_count) " +
                "from block_cache where block_timestamp >= ?::timestamp and block_timestamp <=?::timestamp " +
                "GROUP BY 1 ORDER BY 1 DESC"
        val statement = connection.prepareStatement(query)
        statement.setObject(1, granularity)
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

    fun addSpotlightToCache(spotlightResponse: Spotlight) = transaction {
        SpotlightCacheTable.insertIgnore {
            it[id] = 1
            it[spotlight] = spotlightResponse
            it[lastHit] = DateTime.now()
        }
    }

    fun getSpotlight() = transaction {
        var spotlightRecord = SpotlightCacheTable.select { (SpotlightCacheTable.id eq 1) }.firstOrNull()
        if (spotlightRecord != null && DateTime.now().millis - spotlightRecord[lastHit].millis > explorerProperties.spotlightTtlMs()) {
            SpotlightCacheTable.deleteWhere { (SpotlightCacheTable.id eq 1) }
            spotlightRecord = null
        }
        if (spotlightRecord != null) spotlightRecord[spotlight] else spotlightRecord
    }

    fun getStakingValidator() = transaction {
        var spotlightRecord = SpotlightCacheTable.select { (SpotlightCacheTable.id eq 1) }.firstOrNull()
        if (spotlightRecord != null && DateTime.now().millis - spotlightRecord[lastHit].millis > explorerProperties.spotlightTtlMs()) {
            SpotlightCacheTable.deleteWhere { (SpotlightCacheTable.id eq 1) }
            spotlightRecord = null
        }
        if (spotlightRecord != null) spotlightRecord[spotlight] else spotlightRecord
    }

}