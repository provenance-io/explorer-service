package io.provenance.explorer.domain.entities

import cosmos.base.tendermint.v1beta1.Query
import cosmos.staking.v1beta1.Staking
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.extensions.execAndMap
import io.provenance.explorer.domain.extensions.mapper
import io.provenance.explorer.domain.models.explorer.CurrentValidatorState
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.sql.ResultSet

object ValidatorsCacheTable : CacheIdTable<Int>(name = "validators_cache") {
    val height = integer("height")
    override val id = height.entityId()
    val validators = jsonb<ValidatorsCacheTable, Query.GetValidatorSetByHeightResponse>("validators", OBJECT_MAPPER)
}

class ValidatorsCacheRecord(id: EntityID<Int>) : CacheEntity<Int>(id) {
    companion object : CacheEntityClass<Int, ValidatorsCacheRecord>(ValidatorsCacheTable) {
        fun insertIgnore(blockHeight: Int, json: Query.GetValidatorSetByHeightResponse) =
            transaction {
                ValidatorsCacheTable.insertIgnore {
                    it[this.height] = blockHeight
                    it[this.validators] = json
                    it[this.hitCount] = 0
                    it[this.lastHit] = DateTime.now()
                }.let { json }
            }

        fun getMissingBlocks() = transaction {
            BlockCacheTable.leftJoin(ValidatorsCacheTable, { BlockCacheTable.height }, { ValidatorsCacheTable.height })
                .slice(BlockCacheTable.height)
                .select { (ValidatorsCacheTable.height.isNull()) }
                .map { it[BlockCacheTable.height] }
                .toSet()
        }
    }

    var height by ValidatorsCacheTable.height
    var validators by ValidatorsCacheTable.validators
    override var lastHit by ValidatorsCacheTable.lastHit
    override var hitCount by ValidatorsCacheTable.hitCount
}

object StakingValidatorCacheTable : IntIdTable(name = "staking_validator_cache") {
    val operatorAddress = varchar("operator_address", 96)
    val consensusPubkey = varchar("consensus_pubkey", 96)
    val accountAddress = varchar("account_address", 96)
    val consensusAddress = varchar("consensus_address", 96)
}

class StakingValidatorCacheRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StakingValidatorCacheRecord>(StakingValidatorCacheTable) {

        val logger = logger(StakingValidatorCacheRecord::class)

        fun insertIgnore(
            operator: String,
            account: String,
            consensusPubkey: String,
            consensusAddr: String
        ) =
            transaction {
                StakingValidatorCacheTable.insertIgnoreAndGetId {
                    it[this.operatorAddress] = operator
                    it[this.consensusPubkey] = consensusPubkey
                    it[this.accountAddress] = account
                    it[this.consensusAddress] = consensusAddr
                }.let { it!! }
            }
    }

    var operatorAddress by StakingValidatorCacheTable.operatorAddress
    var consensusPubkey by StakingValidatorCacheTable.consensusPubkey
    var accountAddress by StakingValidatorCacheTable.accountAddress
    var consensusAddress by StakingValidatorCacheTable.consensusAddress
}

object ValidatorStateTable : IntIdTable(name = "validator_state") {
    val operatorAddrId = integer("operator_addr_id")
    val operatorAddress = varchar("operator_address", 128)
    val blockHeight = integer("block_height")
    val moniker = varchar("moniker", 128)
    val status = varchar("status", 64)
    val jailed = bool("jailed")
    val tokenCount = decimal("token_count", 100, 0)
    val json = jsonb<ValidatorStateTable, Staking.Validator>("json", OBJECT_MAPPER)
}

class ValidatorStateRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ValidatorStateRecord>(ValidatorStateTable) {

        val logger = logger(ValidatorStateRecord::class)

        fun insertIgnore(blockHeight: Int, valId: Int, operator: String, json: Staking.Validator) = transaction {
            ValidatorStateTable.insertIgnore {
                it[this.operatorAddrId] = valId
                it[this.operatorAddress] = operator
                it[this.blockHeight] = blockHeight
                it[this.moniker] = json.description.moniker
                it[this.status] = json.status.name
                it[this.jailed] = json.jailed
                it[this.tokenCount] = json.tokens.toBigDecimal()
                it[this.json] = json
            }
        }

        fun refreshCurrentStateView() = transaction {
            val query = "REFRESH MATERIALIZED VIEW current_validator_state"
            this.exec(query)
        }

        fun findAll() = transaction {
            val query = "SELECT * FROM current_validator_state".trimIndent()
            query.execAndMap { it.toCurrentValidatorState() }
        }

        fun findByValId(id: Int) = transaction {
            val query = "SELECT * FROM current_validator_state WHERE operator_addr_id = ?".trimIndent()
            val arguments = listOf(Pair(IntegerColumnType(), id))
            query.execAndMap(arguments) { it.toCurrentValidatorState() }.firstOrNull()
        }

        fun findByListValId(ids: List<Int>) = transaction {
            if (ids.isNotEmpty()) {
                val arguments = ids.joinToString(", ", "(", ")")
                val query = "SELECT * FROM current_validator_state WHERE operator_addr_id in $arguments".trimIndent()
                query.execAndMap() { it.toCurrentValidatorState() }
            } else listOf()
        }

        fun findByAccount(address: String) = transaction {
            val query = "SELECT * FROM current_validator_state WHERE account_address = ?".trimIndent()
            val arguments = listOf(Pair(VarCharColumnType(128), address))
            query.execAndMap(arguments) { it.toCurrentValidatorState() }.firstOrNull()
        }

        fun findByConsensusAddress(address: String) = transaction {
            val query = "SELECT * FROM current_validator_state WHERE consensus_address = ?".trimIndent()
            val arguments = listOf(Pair(VarCharColumnType(128), address))
            query.execAndMap(arguments) { it.toCurrentValidatorState() }.firstOrNull()
        }

        fun findByOperator(address: String) = transaction {
            val query = "SELECT * FROM current_validator_state WHERE operator_address = ?".trimIndent()
            val arguments = listOf(Pair(VarCharColumnType(128), address))
            query.execAndMap(arguments) { it.toCurrentValidatorState() }.firstOrNull()
        }

        fun findByStatus(status: String, valSet: List<String>? = null, offset: Int? = null, limit: Int? = null) = transaction {
            val inSet = valSet?.joinToString("', '", "('", "')")
            val whereClause = if (inSet != null) "consensus_address in $inSet" else null
            val andWhere = if (whereClause != null) "AND $whereClause" else ""
            val limitOffset = if (offset != null && limit != null) " OFFSET $offset LIMIT $limit" else ""

            when (status) {
                "active" -> {
                    val query = """SELECT * FROM current_validator_state 
                        WHERE status = ? $andWhere 
                        ORDER BY token_count DESC
                        $limitOffset
                    """.trimIndent()
                    val arguments = listOf(Pair(VarCharColumnType(64), Staking.BondStatus.BOND_STATUS_BONDED.name))
                    query.execAndMap(arguments) { it.toCurrentValidatorState() }
                }
                "jailed" -> {
                    val query = """SELECT * FROM current_validator_state 
                        WHERE jailed = true $andWhere 
                        ORDER BY token_count DESC
                        $limitOffset
                    """.trimIndent()
                    query.execAndMap() { it.toCurrentValidatorState() }
                }
                "candidate" -> {
                    val query = """SELECT * FROM current_validator_state 
                        WHERE status != ? AND jailed = false $andWhere 
                        ORDER BY token_count DESC
                        $limitOffset
                    """.trimIndent()
                    val arguments = listOf(Pair(VarCharColumnType(64), Staking.BondStatus.BOND_STATUS_BONDED.name))
                    query.execAndMap(arguments) { it.toCurrentValidatorState() }
                }
                "all" -> {
                    val where = if (whereClause != null) "WHERE $whereClause " else ""
                    val query = "SELECT * FROM current_validator_state $where ORDER BY token_count DESC $limitOffset".trimIndent()
                    query.execAndMap() { it.toCurrentValidatorState() }
                }
                else -> listOf<CurrentValidatorState>()
                    .also { logger.error("This status is not supported: $status") }
            }
        }

        fun findByStatusCount(status: String, valSet: List<String>? = null) = transaction {
            val inSet = valSet?.joinToString("', '", "('", "')")
            val whereClause = if (inSet != null) "consensus_address in $inSet" else null
            val andWhere = if (whereClause != null) "AND $whereClause" else ""

            when (status) {
                "active" -> {
                    val query = "SELECT count(*) AS count FROM current_validator_state WHERE status = ? $andWhere".trimIndent()
                    val arguments = listOf(Pair(VarCharColumnType(64), Staking.BondStatus.BOND_STATUS_BONDED.name))
                    query.execAndMap(arguments) { it.toCount() }.first()
                }
                "jailed" -> {
                    val query = "SELECT count(*) AS count FROM current_validator_state WHERE jailed = true $andWhere".trimIndent()
                    query.execAndMap() { it.toCount() }.first()
                }
                "candidate" -> {
                    val query =
                        "SELECT count(*) AS count FROM current_validator_state WHERE status != ? AND jailed = false $andWhere".trimIndent()
                    val arguments = listOf(Pair(VarCharColumnType(64), Staking.BondStatus.BOND_STATUS_BONDED.name))
                    query.execAndMap(arguments) { it.toCount() }.first()
                }
                "all" -> {
                    val where = if (whereClause != null) "WHERE $whereClause " else ""
                    val query = "SELECT count(*) AS count FROM current_validator_state $where".trimIndent()
                    query.execAndMap() { it.toCount() }.first()
                }
                else -> 0.toLong().also { logger.error("This status is not supported: $status") }
            }
        }
    }

    var operatorAddrId by ValidatorStateTable.operatorAddrId
    var operatorAddress by ValidatorStateTable.operatorAddress
    var blockHeight by ValidatorStateTable.blockHeight
    var moniker by ValidatorStateTable.moniker
    var status by ValidatorStateTable.status
    var jailed by ValidatorStateTable.jailed
    var tokenCount by ValidatorStateTable.tokenCount
    var json by ValidatorStateTable.json
}

fun ResultSet.toCurrentValidatorState() = CurrentValidatorState(
    this.getInt("operator_addr_id"),
    this.getString("operator_address"),
    this.getInt("block_height"),
    this.getString("moniker"),
    this.getString("status"),
    this.getBoolean("jailed"),
    this.getBigDecimal("token_count"),
    this.getString("json").mapper(Staking.Validator::class.java),
    this.getString("account_address"),
    this.getString("consensus_address"),
    this.getString("consensus_pubkey")
)

fun ResultSet.toCount() = this.getLong("count")
