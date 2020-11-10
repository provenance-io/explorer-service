package io.provenance.explorer.service

import com.fasterxml.jackson.databind.JsonNode
import io.provenance.core.extensions.logger
import io.provenance.explorer.config.ServiceProperties
import io.provenance.explorer.domain.BlockCacheTable
import io.provenance.explorer.domain.ValidatorsCacheTable
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.joda.time.DateTime
import org.springframework.stereotype.Service

@Service
class CacheService() {

    protected val logger = logger(CacheService::class)

    fun getBlockByHeight(blockHeight: Long) = transaction {
        var jsonNode: JsonNode? = null
        var block = BlockCacheTable.select { (BlockCacheTable.height eq blockHeight) }.firstOrNull()?.let {
            it
        }
        if (block != null) {
            BlockCacheTable.update({ BlockCacheTable.height eq blockHeight }) {
                it[hitCount] = block[hitCount] + 1
                it[lastHit] = DateTime.now()
            }
            jsonNode = block[BlockCacheTable.data]
        }
        jsonNode
    }

    fun addBlockToCache(blockHeight: Long, json: JsonNode) = transaction {
        BlockCacheTable.insertIgnore {
            it[height] = blockHeight
            it[data] = json
            it[hitCount] = 0
            it[lastHit] = DateTime.now()
        }
    }

    fun getValidatorsByHeight(blockHeight: Long) = transaction {
        var jsonNode: JsonNode? = null
        var validators = ValidatorsCacheTable.select { (ValidatorsCacheTable.height eq blockHeight) }.firstOrNull()?.let {
            it
        }
        if (validators != null) {
            ValidatorsCacheTable.update({ ValidatorsCacheTable.height eq blockHeight }) {
                it[hitCount] = validators[hitCount] + 1
                it[lastHit] = DateTime.now()
            }
            jsonNode = validators[ValidatorsCacheTable.data]
        }
        jsonNode
    }

    fun addValidatorsToCache(blockHeight: Long, json: JsonNode) = transaction {
        ValidatorsCacheTable.insertIgnore {
            it[height] = blockHeight
            it[data] = json
            it[hitCount] = 0
            it[lastHit] = DateTime.now()
        }
    }
}