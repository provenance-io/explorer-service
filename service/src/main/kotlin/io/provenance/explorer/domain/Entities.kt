package io.provenance.explorer.domain

import com.fasterxml.jackson.databind.JsonNode
import org.jetbrains.exposed.sql.Table
import io.provenance.core.exposed.sql.jsonb
import io.provenance.explorer.OBJECT_MAPPER

object BlockCacheTable : Table(name = "block_cache") {
    val height = integer("height").primaryKey()
    val block = jsonb<BlockCacheTable, BlockMeta>("block", OBJECT_MAPPER)
    val blockTimestamp = datetime("block_timestamp")
    val txCount = integer("tx_count")
    val lastHit = datetime("last_hit")
    val hitCount = integer("hit_count")
}

object BlockIndexTable : Table(name = "block_index") {
    val id = integer("id").primaryKey()
    val maxHeightRead = integer("max_height_read")
    val minHeightRead = integer("min_height_read")
    val lastUpdate = datetime("last_update")
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
    val height = integer("height")
    val txType = varchar("tx_type", 64)
    val gasWanted = integer("gas_wanted")
    val gasUsed = integer("gas_used")
    val txTimestamp = datetime("tx_timestamp")
    val errorCode = integer("error_code")
    val codespace= varchar("codespace", 16)
    val tx = jsonb<TransactionCacheTable, PbTransaction>("tx", OBJECT_MAPPER)
    val lastHit = datetime("last_hit")
    val hitCount = integer("hit_count")

}

object SpotlightCacheTable : Table(name = "spotlight_cache") {
    val id = integer("id").primaryKey()
    val spotlight = jsonb<SpotlightCacheTable, Spotlight>("spotlight", OBJECT_MAPPER)
    val lastHit = datetime("last_hit")
}

object ValidatorAdressesTable : Table(name = "validator_addresses") {
    val consensusAddress = varchar("consensus_address", 96)
    val consensusPubKeyAddress = varchar("consensus_pubkey_address", 96)
    val operatorAddress = varchar("operator_address", 96)
}

data class ValidatorAddresses(val consensusAddress: String, val consensusPubKeyAddress: String, val operatorAddress: String)