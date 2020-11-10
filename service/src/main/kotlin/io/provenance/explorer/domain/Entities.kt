package io.provenance.explorer.domain

import com.fasterxml.jackson.databind.JsonNode
import org.jetbrains.exposed.sql.Table
import io.provenance.core.exposed.sql.jsonb
import io.provenance.explorer.OBJECT_MAPPER

object BlockCacheTable : Table(name = "block_cache") {
    val height = long("height")
    val data = jsonb<BlockCacheTable, JsonNode>("block", OBJECT_MAPPER)
    val lastHit = datetime("last_hit")
    val hitCount = integer("hit_count")
}

object ValidatorsCacheTable: Table(name="validators_cache") {
    val height = long("height")
    val data = jsonb<ValidatorsCacheTable, JsonNode>("validators", OBJECT_MAPPER)
    val lastHit = datetime("last_hit")
    val hitCount = integer("hit_count")
}

//object TransactionBlockCacheTable : Table(name = "transaction_block_cache") {
//    val height = long("height")
//    val page = integer("page")
//    val perPage = integer("per_page")
//    val sortOrder = varchar("sort_order", 16)
//    val result = jsonb<TransactionBlockCacheTable, JsonNode>("result", OBJECT_MAPPER)
//    val lastHit = date("last_hit")
//    val hitCount = integer("hit_count")
//}
//
//object LatestBlockHeightTable : Table(name = "") {
//    val height = long("latest_height")
//    val lastUpdate = date("last_update")
//}