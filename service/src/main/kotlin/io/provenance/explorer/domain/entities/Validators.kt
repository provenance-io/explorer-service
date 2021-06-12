package io.provenance.explorer.domain.entities

import cosmos.base.tendermint.v1beta1.Query
import cosmos.staking.v1beta1.Staking
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.sql.jsonb
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime


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
    val stakingValidator = jsonb<StakingValidatorCacheTable, Staking.Validator>("staking_validator", OBJECT_MAPPER)
    val moniker = varchar("moniker", 128)
    val status = varchar("status", 64)
    val jailed = bool("jailed")
    val tokenCount = decimal("token_count", 100, 0)
}

class StakingValidatorCacheRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StakingValidatorCacheRecord>(StakingValidatorCacheTable) {

        val logger = logger(StakingValidatorCacheRecord::class)

        fun insertIgnore(operator: String, account: String, consensusPubkey: String, consensusAddr: String, json: Staking.Validator) =
            transaction {
                StakingValidatorCacheTable.insertIgnoreAndGetId {
                    it[this.operatorAddress] = operator
                    it[this.stakingValidator] = json
                    it[this.moniker] = json.description.moniker
                    it[this.status] = json.status.name
                    it[this.jailed] = json.jailed
                    it[this.tokenCount] = json.tokens.toBigDecimal()
                    it[this.consensusPubkey] = consensusPubkey
                    it[this.accountAddress] = account
                    it[this.consensusAddress] = consensusAddr
                }.let { Pair(it!!, json) }
            }

        fun findByAccount(address: String) = transaction {
            StakingValidatorCacheRecord.find { StakingValidatorCacheTable.accountAddress eq address }.firstOrNull()
        }

        fun findByConsensusPubkey(pubkey: String) = transaction {
            StakingValidatorCacheRecord.find { StakingValidatorCacheTable.consensusPubkey eq pubkey }.firstOrNull()
        }

        fun findByConsensusAddress(address: String) = transaction {
            StakingValidatorCacheRecord.find { StakingValidatorCacheTable.consensusAddress eq address }.firstOrNull()
        }

        fun findByOperator(address: String) = transaction {
            StakingValidatorCacheRecord.find { StakingValidatorCacheTable.operatorAddress eq address }.firstOrNull()
        }

        fun findByStatus(status: String) = transaction {
            when (status) {
                "active" -> StakingValidatorCacheRecord.find {
                    StakingValidatorCacheTable.status eq Staking.BondStatus.BOND_STATUS_BONDED.name }
                    .orderBy(Pair(StakingValidatorCacheTable.tokenCount, SortOrder.DESC))
                "jailed" -> StakingValidatorCacheRecord.find { StakingValidatorCacheTable.jailed eq true }
                    .orderBy(Pair(StakingValidatorCacheTable.tokenCount, SortOrder.DESC))
                "candidate" -> StakingValidatorCacheRecord.find {
                    (StakingValidatorCacheTable.status neq Staking.BondStatus.BOND_STATUS_BONDED.name) and
                        (StakingValidatorCacheTable.jailed eq false) }
                    .orderBy(Pair(StakingValidatorCacheTable.tokenCount, SortOrder.DESC))
                "all" -> StakingValidatorCacheRecord.all()
                    .orderBy(Pair(StakingValidatorCacheTable.tokenCount, SortOrder.DESC))
                else -> listOf<StakingValidatorCacheRecord>()
                    .also { logger.error("This status is not supported: $status") }
            }
        }

        fun findNotJailed() = transaction {
            StakingValidatorCacheRecord.find { StakingValidatorCacheTable.jailed eq false }
                .orderBy(Pair(StakingValidatorCacheTable.tokenCount, SortOrder.DESC))
        }
    }

    var operatorAddress by StakingValidatorCacheTable.operatorAddress
    var stakingValidator by StakingValidatorCacheTable.stakingValidator
    var moniker by StakingValidatorCacheTable.moniker
    var status by StakingValidatorCacheTable.status
    var jailed by StakingValidatorCacheTable.jailed
    var tokenCount by StakingValidatorCacheTable.tokenCount
    var consensusPubkey by StakingValidatorCacheTable.consensusPubkey
    var accountAddress by StakingValidatorCacheTable.accountAddress
    var consensusAddress by StakingValidatorCacheTable.consensusAddress
}

