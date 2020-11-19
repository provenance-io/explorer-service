package io.provenance.explorer.domain

import com.fasterxml.jackson.databind.JsonNode
import org.jetbrains.exposed.sql.Table
import io.provenance.core.exposed.sql.jsonb
import io.provenance.explorer.OBJECT_MAPPER

object BlockCacheTable : Table(name = "block_cache") {
    val height = integer("height").primaryKey()
    val block = jsonb<BlockCacheTable, JsonNode>("block", OBJECT_MAPPER)
    val blockTimestamp = datetime("block_timestamp")
    val lastHit = datetime("last_hit")
    val hitCount = integer("hit_count")
}

object BlockchainCacheTable : Table(name = "blockchain_cache") {
    val maxHeight = integer("max_height").primaryKey()
    val blocks = jsonb<BlockchainCacheTable, JsonNode>("blocks", OBJECT_MAPPER)
    val lastHit = datetime("last_hit")
    val hitCount = integer("hit_count")
}

object ValidatorsCacheTable : Table(name = "validators_cache") {
    val height = integer("height").primaryKey()
    val validators = jsonb<ValidatorsCacheTable, JsonNode>("validators", OBJECT_MAPPER)
    val lastHit = datetime("last_hit")
    val hitCount = integer("hit_count")
}

object ValidatorCacheTable : Table(name = "validator_cache") {
    val hash = varchar("addressId", 64).primaryKey()
    val validator = jsonb<ValidatorCacheTable, JsonNode>("validator", OBJECT_MAPPER)
    val lastHit = datetime("last_hit")
    val hitCount = integer("hit_count")
}

object TransactionCacheTable : Table(name = "transaction_cache") {
    val hash = varchar("hash", 64).primaryKey()
    val tx = jsonb<TransactionCacheTable, JsonNode>("tx", OBJECT_MAPPER)
    val lastHit = datetime("last_hit")
    val hitCount = integer("hit_count")

}

object TransactionCountIndex : Table(name = "transation_count_index") {
    val id = integer("id").primaryKey()
    val maxHeightRead = integer("max_height_read")
    val minHeightRead = integer("min_height_read")
    val lastRunStart = datetime("last_run_start")
    val lastRunEnd = datetime("last_run_end")
}

object TransactionCountTable : Table(name = "transaction_count_cache") {
    val day = varchar("day", 16).primaryKey()
    val numberTxs = integer("number_txs")
    val numberTxBlocks = integer("number_tx_blocks")
    val maxHeight = integer("max_height")
    val minHeight = integer("min_height")
}