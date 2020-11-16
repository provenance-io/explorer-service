package io.provenance.explorer.domain

import com.fasterxml.jackson.databind.JsonNode
import org.jetbrains.exposed.sql.Table
import io.provenance.core.exposed.sql.jsonb
import io.provenance.explorer.OBJECT_MAPPER

object BlockCacheTable : Table(name = "block_cache") {
    val height = integer("height")
    val block = jsonb<BlockCacheTable, JsonNode>("block", OBJECT_MAPPER)
    val lastHit = datetime("last_hit")
    val hitCount = integer("hit_count")
}

object BlockchainCacheTable : Table(name = "blockchain_cache") {
    val maxHeight = integer("max_height")
    val blocks = jsonb<BlockchainCacheTable, JsonNode>("blocks", OBJECT_MAPPER)
    val lastHit = datetime("last_hit")
    val hitCount = integer("hit_count")
}

object ValidatorsCacheTable : Table(name = "validators_cache") {
    val height = integer("height")
    val validators = jsonb<ValidatorsCacheTable, JsonNode>("validators", OBJECT_MAPPER)
    val lastHit = datetime("last_hit")
    val hitCount = integer("hit_count")
}

object TransactionCacheTable : Table(name = "transaction_cache") {
    val hash = varchar("hash", 64)
    val tx = jsonb<TransactionCacheTable, JsonNode>("tx", OBJECT_MAPPER)
    val lastHit = datetime("last_hit")
    val hitCount = integer("hit_count")

}

object TransactionCountTable : Table(name = "transaction_count_cache") {
    val day = varchar("day", 16)
    val numberTxs = integer("number_txs")
    val numberTxBlocks = integer("number_tx_blocks")
    val complete = bool("complete")
    val maxHeight = integer("max_height")
    val minHeight = integer("min_height")
    val indexHeight = integer("index_height")
}