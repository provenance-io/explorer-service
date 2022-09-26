package io.provenance.explorer.domain.entities

import cosmos.base.tendermint.v1beta1.Query
import cosmos.staking.v1beta1.Staking
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.sql.ArrayColumnType
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.core.sql.toProcedureObject
import io.provenance.explorer.domain.entities.ValidatorState.ACTIVE
import io.provenance.explorer.domain.entities.ValidatorState.ALL
import io.provenance.explorer.domain.entities.ValidatorState.CANDIDATE
import io.provenance.explorer.domain.entities.ValidatorState.JAILED
import io.provenance.explorer.domain.entities.ValidatorState.REMOVED
import io.provenance.explorer.domain.extensions.execAndMap
import io.provenance.explorer.domain.extensions.mapper
import io.provenance.explorer.domain.extensions.toDecimal
import io.provenance.explorer.domain.models.explorer.CurrentValidatorState
import io.provenance.explorer.domain.models.explorer.TxData
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.math.BigDecimal
import java.sql.ResultSet

object ValidatorsCacheTable : CacheIdTable<Int>(name = "validators_cache") {
    val height = integer("height")
    override val id = height.entityId()
    val validators = jsonb<ValidatorsCacheTable, Query.GetValidatorSetByHeightResponse>("validators", OBJECT_MAPPER)
}

class ValidatorsCacheRecord(id: EntityID<Int>) : CacheEntity<Int>(id) {
    companion object : CacheEntityClass<Int, ValidatorsCacheRecord>(ValidatorsCacheTable) {

        fun buildInsert(blockHeight: Int, json: Query.GetValidatorSetByHeightResponse) =
            listOf(blockHeight, json, DateTime.now(), 0).toProcedureObject()

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

        fun findByOperAddr(operAddr: String) = transaction {
            StakingValidatorCacheRecord.find { StakingValidatorCacheTable.operatorAddress eq operAddr }
                .firstOrNull()
        }

        fun insertIgnore(
            operator: String,
            account: String,
            consensusPubkey: String,
            consensusAddr: String
        ) =
            transaction {
                findByOperAddr(operator)
                    ?: StakingValidatorCacheTable.insertIgnoreAndGetId {
                        it[this.operatorAddress] = operator
                        it[this.consensusPubkey] = consensusPubkey
                        it[this.accountAddress] = account
                        it[this.consensusAddress] = consensusAddr
                    }.let { findById(it!!)!! }
            }
    }

    var operatorAddress by StakingValidatorCacheTable.operatorAddress
    var consensusPubkey by StakingValidatorCacheTable.consensusPubkey
    var accountAddress by StakingValidatorCacheTable.accountAddress
    var consensusAddress by StakingValidatorCacheTable.consensusAddress
}

enum class ValidatorState { ACTIVE, CANDIDATE, JAILED, REMOVED, ALL }

object ValidatorStateTable : IntIdTable(name = "validator_state") {
    val operatorAddrId = integer("operator_addr_id")
    val operatorAddress = varchar("operator_address", 128)
    val blockHeight = integer("block_height")
    val moniker = varchar("moniker", 128)
    val status = varchar("status", 64)
    val jailed = bool("jailed")
    val tokenCount = decimal("token_count", 100, 0)
    val json = jsonb<ValidatorStateTable, Staking.Validator>("json", OBJECT_MAPPER)
    val commissionRate = decimal("commission_rate", 19, 18)
    val removed = bool("removed")
}

class ValidatorStateRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ValidatorStateRecord>(ValidatorStateTable) {

        val logger = logger(ValidatorStateRecord::class)

        fun insertIgnore(blockHeight: Int, valId: Int, operator: String, json: Staking.Validator) =
            transaction {
                ValidatorStateTable.insertIgnore {
                    it[this.operatorAddrId] = valId
                    it[this.operatorAddress] = operator
                    it[this.blockHeight] = blockHeight
                    it[this.moniker] = json.description.moniker
                    it[this.status] = json.status.name
                    it[this.jailed] = json.jailed
                    it[this.tokenCount] = json.tokens.toBigDecimal()
                    it[this.json] = json
                    it[this.commissionRate] = json.commission.commissionRates.rate.toDecimal()
                    it[this.removed] = json.tokens.toBigDecimal() == BigDecimal.ZERO
                }
            }

        fun getCommissionHistory(operator: String) = transaction {
            ValidatorStateRecord.find { ValidatorStateTable.operatorAddress eq operator }
                .orderBy(Pair(ValidatorStateTable.blockHeight, SortOrder.ASC))
                .toList()
        }

        fun refreshCurrentStateView() = transaction {
            val query = "REFRESH MATERIALIZED VIEW current_validator_state"
            this.exec(query)
        }

        fun findAll(activeSet: Int) = transaction {
            val query = "SELECT * FROM get_all_validator_state(?, ?, NULL)".trimIndent()
            val arguments = mutableListOf<Pair<ColumnType, *>>(
                Pair(IntegerColumnType(), activeSet),
                Pair(VarCharColumnType(64), Staking.BondStatus.BOND_STATUS_BONDED.name),
            )
            query.execAndMap(arguments) { it.toCurrentValidatorState() }
        }

        fun findByValId(activeSet: Int, id: Int) = transaction {
            val query = "SELECT * FROM get_all_validator_state(?, ?, NULL) WHERE operator_addr_id = ?".trimIndent()
            val arguments = mutableListOf<Pair<ColumnType, *>>(
                Pair(IntegerColumnType(), activeSet),
                Pair(VarCharColumnType(64), Staking.BondStatus.BOND_STATUS_BONDED.name),
                Pair(IntegerColumnType(), id)
            )
            query.execAndMap(arguments) { it.toCurrentValidatorState() }.firstOrNull()
        }

        fun findByListValId(activeSet: Int, ids: List<Int>) = transaction {
            if (ids.isNotEmpty()) {
                val idList = ids.joinToString(", ", "(", ")")
                val query =
                    "SELECT * FROM get_all_validator_state(?, ?, NULL) WHERE operator_addr_id IN $idList".trimIndent()
                val arguments = mutableListOf<Pair<ColumnType, *>>(
                    Pair(IntegerColumnType(), activeSet),
                    Pair(VarCharColumnType(64), Staking.BondStatus.BOND_STATUS_BONDED.name),
                )
                query.execAndMap(arguments) { it.toCurrentValidatorState() }
            } else listOf()
        }

        fun findByAccount(activeSet: Int, address: String) = transaction {
            val query = "SELECT * FROM get_all_validator_state(?, ?, NULL) WHERE account_address = ?".trimIndent()
            val arguments = mutableListOf<Pair<ColumnType, *>>(
                Pair(IntegerColumnType(), activeSet),
                Pair(VarCharColumnType(64), Staking.BondStatus.BOND_STATUS_BONDED.name),
                Pair(VarCharColumnType(128), address)
            )
            query.execAndMap(arguments) { it.toCurrentValidatorState() }.firstOrNull()
        }

        fun findByOperator(activeSet: Int, address: String) = transaction {
            val query = "SELECT * FROM get_all_validator_state(?, ?, NULL) WHERE operator_address = ?".trimIndent()
            val arguments = mutableListOf<Pair<ColumnType, *>>(
                Pair(IntegerColumnType(), activeSet),
                Pair(VarCharColumnType(64), Staking.BondStatus.BOND_STATUS_BONDED.name),
                Pair(VarCharColumnType(128), address)
            )
            query.execAndMap(arguments) { it.toCurrentValidatorState() }.firstOrNull()
        }

        fun findByConsensusAddress(activeSet: Int, address: String) = transaction {
            val query = "SELECT * FROM get_all_validator_state(?, ?, NULL) WHERE consensus_address = ?".trimIndent()
            val arguments = mutableListOf<Pair<ColumnType, *>>(
                Pair(IntegerColumnType(), activeSet),
                Pair(VarCharColumnType(64), Staking.BondStatus.BOND_STATUS_BONDED.name),
                Pair(VarCharColumnType(128), address)
            )
            query.execAndMap(arguments) { it.toCurrentValidatorState() }.firstOrNull()
        }

        fun findByConsensusAddressIn(activeSet: Int, addresses: List<String>) = transaction {
            val query = "SELECT * FROM get_all_validator_state(?, ?, ?) ".trimIndent()
            val arguments = mutableListOf<Pair<ColumnType, *>>(
                Pair(IntegerColumnType(), activeSet),
                Pair(VarCharColumnType(64), Staking.BondStatus.BOND_STATUS_BONDED.name),
                Pair(ArrayColumnType(TextColumnType()), addresses.ifEmpty { null })
            )
            query.execAndMap(arguments) { it.toCurrentValidatorState() }
        }

        fun findByStatus(
            activeSet: Int,
            searchState: ValidatorState,
            consensusAddrSet: List<String>? = null,
            offset: Int? = null,
            limit: Int? = null
        ) =
            transaction {
                when (searchState) {
                    ACTIVE, JAILED, CANDIDATE, REMOVED -> {
                        val offsetDefault = offset ?: 0
                        val limitDefault = limit ?: 10000
                        val query = "SELECT * FROM get_validator_list(?, ?, ?, ?, ?, ?) "
                        val arguments = mutableListOf<Pair<ColumnType, *>>(
                            Pair(IntegerColumnType(), activeSet),
                            Pair(VarCharColumnType(64), Staking.BondStatus.BOND_STATUS_BONDED.name),
                            Pair(TextColumnType(), searchState.name.lowercase()),
                            Pair(IntegerColumnType(), limitDefault),
                            Pair(IntegerColumnType(), offsetDefault),
                            Pair(ArrayColumnType(TextColumnType()), consensusAddrSet)
                        )
                        query.execAndMap(arguments) { it.toCurrentValidatorState() }
                    }
                    ALL -> {
                        val limitOffset = if (offset != null && limit != null) " OFFSET $offset LIMIT $limit" else ""
                        val query =
                            "SELECT * FROM get_all_validator_state(?, ?, ?) ORDER BY token_count DESC $limitOffset".trimIndent()
                        val arguments = mutableListOf<Pair<ColumnType, *>>(
                            Pair(IntegerColumnType(), activeSet),
                            Pair(VarCharColumnType(64), Staking.BondStatus.BOND_STATUS_BONDED.name),
                            Pair(ArrayColumnType(TextColumnType()), consensusAddrSet)
                        )
                        query.execAndMap(arguments) { it.toCurrentValidatorState() }
                    }
                }
            }

        fun findByStatusCount(
            activeSet: Int,
            searchState: ValidatorState,
            consensusAddrSet: List<String>? = null
        ) = transaction {
            when (searchState) {
                ACTIVE, JAILED, CANDIDATE, REMOVED -> {
                    val offsetDefault = 0
                    val limitDefault = 10000
                    val query = "SELECT count(*) AS count FROM get_validator_list(?, ?, ?, ?, ?, ?) "
                    val arguments = mutableListOf<Pair<ColumnType, *>>(
                        Pair(IntegerColumnType(), activeSet),
                        Pair(VarCharColumnType(64), Staking.BondStatus.BOND_STATUS_BONDED.name),
                        Pair(TextColumnType(), searchState.name.lowercase()),
                        Pair(IntegerColumnType(), limitDefault),
                        Pair(IntegerColumnType(), offsetDefault),
                        Pair(ArrayColumnType(TextColumnType()), consensusAddrSet),
                    )
                    query.execAndMap(arguments) { it.toCount() }.first()
                }
                ALL -> {
                    val query = "SELECT count(*) AS count FROM get_all_validator_state(?, ?, ?)".trimIndent()
                    val arguments = mutableListOf<Pair<ColumnType, *>>(
                        Pair(IntegerColumnType(), activeSet),
                        Pair(VarCharColumnType(64), Staking.BondStatus.BOND_STATUS_BONDED.name),
                        Pair(ArrayColumnType(TextColumnType()), consensusAddrSet),
                    )
                    query.execAndMap(arguments) { it.toCount() }.first()
                }
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
    var commissionRate by ValidatorStateTable.commissionRate
    var removed by ValidatorStateTable.removed
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
    this.getString("consensus_pubkey"),
    ValidatorState.valueOf(this.getString("validator_state").uppercase()),
    this.getBigDecimal("commission_rate"),
    this.getBoolean("removed"),
    this.getString("image_url")
)

fun ResultSet.toCount() = this.getLong("count")

object ValidatorMarketRateTable : IntIdTable(name = "validator_market_rate") {
    val blockHeight = integer("block_height")
    val blockTimestamp = datetime("block_timestamp")
    val proposerAddress = varchar("proposer_address", 128)
    val txHashId = integer("tx_hash_id")
    val txHash = varchar("tx_hash", 64)
    val marketRate = decimal("market_rate", 100, 0)
    val success = bool("success")
}

class ValidatorMarketRateRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ValidatorMarketRateRecord>(ValidatorMarketRateTable) {
        fun buildInsert(
            txInfo: TxData,
            proposer: String,
            tx: ServiceOuterClass.GetTxResponse,
            totalBaseFees: BigDecimal
        ) =
            listOf(
                0,
                txInfo.blockHeight,
                txInfo.txTimestamp,
                proposer,
                0,
                txInfo.txHash,
                TxFeeRecord.calcMarketRate(tx, totalBaseFees),
                tx.txResponse.code == 0
            ).toProcedureObject()

        fun getRateByTxId(txId: Int) = transaction {
            ValidatorMarketRateRecord.find { ValidatorMarketRateTable.txHashId eq txId }.first().marketRate
        }

        fun getValidatorRateForBlockCount(operAddr: String, txCount: Int) = transaction {
            ValidatorMarketRateRecord
                .find { (ValidatorMarketRateTable.proposerAddress eq operAddr) and (ValidatorMarketRateTable.success) }
                .orderBy(Pair(ValidatorMarketRateTable.blockHeight, SortOrder.DESC))
                .limit(txCount)
                .toList()
        }

        fun findForDates(fromDate: DateTime, toDate: DateTime, address: String?) = transaction {
            val query = ValidatorMarketRateTable
                .select { ValidatorMarketRateTable.blockTimestamp.between(fromDate, toDate.plusDays(1)) }
            if (address != null)
                query.andWhere { ValidatorMarketRateTable.proposerAddress eq address }
            ValidatorMarketRateRecord.wrapRows(query)
        }

        fun getChainRateForBlockCount(blockCount: Int) = transaction {
            ValidatorMarketRateRecord.find { ValidatorMarketRateTable.success eq true }
                .orderBy(Pair(ValidatorMarketRateTable.blockHeight, SortOrder.DESC))
                .limit(blockCount)
                .toList()
        }
    }

    var blockHeight by ValidatorMarketRateTable.blockHeight
    var blockTimestamp by ValidatorMarketRateTable.blockTimestamp
    var proposerAddress by ValidatorMarketRateTable.proposerAddress
    var txHashId by ValidatorMarketRateTable.txHashId
    var txHash by ValidatorMarketRateTable.txHash
    var marketRate by ValidatorMarketRateTable.marketRate
    var success by ValidatorMarketRateTable.success
}
