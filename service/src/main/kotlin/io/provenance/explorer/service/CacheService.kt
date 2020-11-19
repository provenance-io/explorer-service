package io.provenance.explorer.service

import com.fasterxml.jackson.databind.JsonNode
import io.provenance.core.extensions.logger
import io.provenance.explorer.domain.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service

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

    fun addBlockToCache(blockHeight: Int, json: JsonNode) = transaction {
        if (shouldCacheBlock(blockHeight, json)) BlockCacheTable.insertIgnore {
            it[height] = blockHeight
            it[block] = json
            it[blockTimestamp] = DateTime.parse(json.get("block").get("header").get("time").asText())
            it[hitCount] = 0
            it[lastHit] = DateTime.now()
        }
    }

    fun shouldCacheBlock(blockHeight: Int, json: JsonNode) = json.get("block").get("header").get("height").asInt() == blockHeight

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

    fun getTransactionCountByDay(day: String) = transaction {
        TransactionCountTable.select { (TransactionCountTable.day eq day) }.firstOrNull()
    }

    fun getTransactionCountIndex() = transaction {
        TransactionCountIndex.select { (TransactionCountIndex.id eq 1) }.firstOrNull()
    }

    fun getTransactionCounts(fromDate: String, toDate: String) = transaction {
        TransactionCountTable.select {
            (TransactionCountTable.day greaterEq fromDate) and
                    (TransactionCountTable.day lessEq toDate)
        }.orderBy(TransactionCountTable.day, SortOrder.DESC).map {
            TxHistory(it[TransactionCountTable.day],
                    it[TransactionCountTable.numberTxs],
                    it[TransactionCountTable.numberTxBlocks],
                    it[TransactionCountTable.maxHeight],
                    it[TransactionCountTable.minHeight])
        }

    }

    fun getTransactionCountsToDate(day: String) = transaction {
        TransactionCountTable.select { TransactionCountTable.day lessEq day }.orderBy(TransactionCountTable.day, SortOrder.DESC).map { it }
    }

    fun addTransactionCounts(dayCounts: Map<String, TxHistory>, maxHeightRead: Int, minHeightRead: Int, startTime: DateTime) = transaction {
        val transactionIndex = TransactionCountIndex.select { (TransactionCountIndex.id eq 1) }.firstOrNull()
        if (transactionIndex == null) {
            TransactionCountIndex.insert {
                it[id] = 1
                it[TransactionCountIndex.maxHeightRead] = maxHeightRead
                it[TransactionCountIndex.minHeightRead] = minHeightRead
                it[TransactionCountIndex.lastRunStart] = startTime
                it[lastRunEnd] = DateTime.now()
            }
        } else {
            TransactionCountIndex.update {
                transactionIndex[TransactionCountIndex.maxHeightRead] = maxHeightRead
                transactionIndex[TransactionCountIndex.minHeightRead] = minHeightRead
                it[TransactionCountIndex.lastRunStart] = startTime
                it[lastRunEnd] = DateTime.now()
            }
        }
        dayCounts.values.forEach { metrics ->
            val transactionCount = TransactionCountTable.select { TransactionCountTable.day eq metrics.day }.firstOrNull()
            if (transactionCount == null) {
                TransactionCountTable.insert {
                    it[TransactionCountTable.day] = metrics.day
                    it[TransactionCountTable.maxHeight] = metrics.maxHeight
                    it[TransactionCountTable.minHeight] = metrics.minHeight
                    it[TransactionCountTable.numberTxBlocks] = metrics.numberTxBlocks
                    it[TransactionCountTable.numberTxs] = metrics.numberTxs
                }
            } else {
                TransactionCountTable.update {
                    transactionCount[TransactionCountTable.maxHeight] += metrics.maxHeight
                    transactionCount[TransactionCountTable.numberTxBlocks] += metrics.numberTxBlocks
                    transactionCount[TransactionCountTable.numberTxs] += metrics.numberTxs
                }
            }
        }
    }


}