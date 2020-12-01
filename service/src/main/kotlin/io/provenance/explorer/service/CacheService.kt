package io.provenance.explorer.service

import com.fasterxml.jackson.databind.JsonNode
import io.provenance.core.extensions.logger
import io.provenance.explorer.domain.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class CacheService() {

    protected val logger = logger(CacheService::class)

    fun getBlockByHeight(blockHeight: Int) = transaction {
        var jsonNode: JsonNode? = null
        var block = BlockCacheTable.select { (BlockCacheTable.height eq blockHeight) }.firstOrNull()?.let {
            it
        }
        if (block != null) {
            BlockCacheTable.update({ BlockCacheTable.height eq blockHeight }) {
                it[hitCount] = block[hitCount] + 1
                it[lastHit] = DateTime.now()
            }
            jsonNode = block[BlockCacheTable.block]
        }
        jsonNode
    }

    fun addBlockToCache(blockHeight: Int, transactionCount: Int, timestamp: DateTime, json: JsonNode) = transaction {
        if (shouldCacheBlock(blockHeight, json)) BlockCacheTable.insertIgnore {
            it[height] = blockHeight
            it[block] = json
            it[txCount] = transactionCount
            it[blockTimestamp] = timestamp
            it[hitCount] = 0
            it[lastHit] = DateTime.now()
        }
    }

    fun shouldCacheBlock(blockHeight: Int, json: JsonNode) = json.get("header").get("height").asInt() == blockHeight

    fun getBlockchainFromMaxHeight(maxHeight: Int) = transaction {
        var jsonNode: JsonNode? = null
        var block = BlockchainCacheTable.select { (BlockchainCacheTable.maxHeight eq maxHeight) }.firstOrNull()?.let {
            it
        }
        if (block != null) {
            BlockchainCacheTable.update({ BlockchainCacheTable.maxHeight eq maxHeight }) {
                it[hitCount] = block[hitCount] + 1
                it[lastHit] = DateTime.now()
            }
            jsonNode = block[BlockchainCacheTable.blocks]
        }
        jsonNode
    }

    fun addBlockchainToCache(maxHeight: Int, json: JsonNode) = transaction {
        if (shouldCacheBlockchain(maxHeight, json)) BlockchainCacheTable.insertIgnore {
            it[BlockchainCacheTable.maxHeight] = maxHeight
            it[blocks] = json
            it[hitCount] = 0
            it[lastHit] = DateTime.now()
        }
    }

    fun shouldCacheBlockchain(maxHeight: Int, json: JsonNode) =
            json.get("block_metas")[0].get("header").get("height").asInt() == maxHeight
                    && json.get("block_metas").size() == 20

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
        var jsonNode: JsonNode? = null
        var tx = TransactionCacheTable.select { (TransactionCacheTable.hash eq hash) }.firstOrNull()?.let {
            it
        }
        if (tx != null) {
            TransactionCacheTable.update({ TransactionCacheTable.hash eq hash }) {
                it[hitCount] = tx[hitCount] + 1
                it[lastHit] = DateTime.now()
            }
            jsonNode = tx[TransactionCacheTable.tx]
        }
        jsonNode
    }

    fun addTransactionToCache(txHash: String, json: JsonNode) = transaction {
        if (shouldCacheTransaction(txHash, json)) TransactionCacheTable.insertIgnore {
            it[hash] = txHash
            it[tx] = json
            it[hitCount] = 0
            it[lastHit] = DateTime.now()
        }
    }

    fun shouldCacheTransaction(txHash: String, json: JsonNode) = json.get("hash").asText()!! == txHash

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
        val results = mutableListOf<TxHistory>()
        while (resultSet.next()) {
            results.add(TxHistory(resultSet.getString(1), resultSet.getInt(2)))
        }
        results
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