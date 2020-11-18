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

    fun addTransactionCount(day: String, totalTxs: Int, totalTxBlocks: Int, maxHeight: Int?, minHeight: Int, indexHeight: Int, complete: Boolean) =
            transaction {
                TransactionCountTable.insertIgnore {
                    it[TransactionCountTable.day] = day
                    if (maxHeight != null) it[TransactionCountTable.maxHeight] = maxHeight
                    it[TransactionCountTable.minHeight] = minHeight
                    it[TransactionCountTable.indexHeight] = indexHeight
                    it[TransactionCountTable.numberTxBlocks] = totalTxBlocks
                    it[TransactionCountTable.numberTxs] = totalTxs
                    it[TransactionCountTable.complete] = complete
                }
            }

    fun updateTransactionCount(day: String, totalTxs: Int, totalTxBlocks: Int, maxHeight: Int?, minHeight: Int, indexHeight: Int, complete: Boolean) =
            transaction {
                TransactionCountTable.update {
                    it[TransactionCountTable.day] = day
                    if (maxHeight != null) it[TransactionCountTable.maxHeight] = maxHeight
                    it[TransactionCountTable.minHeight] = minHeight
                    it[TransactionCountTable.indexHeight] = indexHeight
                    it[TransactionCountTable.numberTxBlocks] = totalTxBlocks
                    it[TransactionCountTable.numberTxs] = totalTxs
                    it[TransactionCountTable.complete] = complete
                }
            }

    fun getTransactionCounts(startDay: String, endDay: String) = transaction {
        TransactionCountTable.select {
            (TransactionCountTable.day lessEq startDay) and
                    (TransactionCountTable.day greaterEq endDay)
        }.orderBy(TransactionCountTable.day, SortOrder.DESC).map { it }
    }

    fun getTransactionCountsToDate(day: String) = transaction {
        TransactionCountTable.select { TransactionCountTable.day lessEq day }.orderBy(TransactionCountTable.day, SortOrder.DESC).map { it }
    }

}