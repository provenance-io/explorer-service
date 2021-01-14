package io.provenance.explorer.domain.entities

import com.fasterxml.jackson.databind.JsonNode
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.PbDelegations
import io.provenance.explorer.domain.PbStakingValidator
import io.provenance.explorer.domain.PbValidatorsResponse
import io.provenance.explorer.domain.core.sql.jsonb
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

object ValidatorAddressesTable : IntIdTable(name = "validator_addresses") {
    val consensusAddress = varchar("consensus_address", 96).uniqueIndex()
    val consensusPubKeyAddress = varchar("consensus_pubkey_address", 96).uniqueIndex()
    val operatorAddress = varchar("operator_address", 96).uniqueIndex()
}

class ValidatorAddressesRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ValidatorAddressesRecord>(ValidatorAddressesTable) {
        fun findByConsensusPubKey(address: String) = transaction {
            ValidatorAddressesRecord.find { ValidatorAddressesTable.consensusPubKeyAddress eq address }.firstOrNull()
        }

        fun findByConsensus(address: String) = transaction {
            ValidatorAddressesRecord.find { ValidatorAddressesTable.consensusAddress eq address }.firstOrNull()
        }

        fun findByOperator(address: String) = transaction {
            ValidatorAddressesRecord.find { ValidatorAddressesTable.operatorAddress eq address }.firstOrNull()
        }

        fun insertIgnore(consensus: String, consensusPubKey: String, operator: String) = transaction {
            ValidatorAddressesTable.insertIgnore {
                it[this.consensusAddress] = consensus
                it[this.consensusPubKeyAddress] = consensusPubKey
                it[this.operatorAddress] = operator
            }
        }
    }

    var consensusAddress by ValidatorAddressesTable.consensusAddress
    var consensusPubKeyAddress by ValidatorAddressesTable.consensusPubKeyAddress
    var operatorAddress by ValidatorAddressesTable.operatorAddress
}




object ValidatorsCacheTable : CacheIdTable<Int>(name = "validators_cache") {
    val height = reference("height", BlockCacheTable.height).primaryKey()
    override val id = height.entityId()
    val validators = jsonb<ValidatorsCacheTable, PbValidatorsResponse>("validators", OBJECT_MAPPER)
}

class ValidatorsCacheRecord(id: EntityID<Int>) : CacheEntity<Int>(id) {
    companion object : CacheEntityClass<Int, ValidatorsCacheRecord>(ValidatorsCacheTable) {
        fun insertIgnore(blockHeight: Int, json: PbValidatorsResponse) =
            transaction {
                ValidatorsCacheTable.insertIgnore {
                    it[this.height] = blockHeight
                    it[this.validators] = json
                    it[this.hitCount] = 0
                    it[this.lastHit] = DateTime.now()
                }.let { json }
            }
    }

    var height by ValidatorsCacheTable.height
    var validators by ValidatorsCacheTable.validators
    override var lastHit by ValidatorsCacheTable.lastHit
    override var hitCount by ValidatorsCacheTable.hitCount
}



object StakingValidatorCacheTable : CacheIdTable<String>(name = "staking_validator_cache") {
    val operatorAddress = reference("operator_address", ValidatorAddressesTable.operatorAddress).primaryKey()
    override val id = operatorAddress.entityId()
    val stakingValidator = jsonb<StakingValidatorCacheTable, PbStakingValidator>("staking_validator", OBJECT_MAPPER)
}

class StakingValidatorCacheRecord(id: EntityID<String>) : CacheEntity<String>(id) {
    companion object : CacheEntityClass<String, StakingValidatorCacheRecord>(StakingValidatorCacheTable) {
        fun insertIgnore(operatorAddress: String, json: PbStakingValidator) =
            transaction {
                StakingValidatorCacheTable.insertIgnore {
                    it[this.operatorAddress] = operatorAddress
                    it[this.stakingValidator] = json
                    it[this.hitCount] = 0
                    it[this.lastHit] = DateTime.now()
                }.let { json }
            }
    }

    var operatorAddress by StakingValidatorCacheTable.operatorAddress
    var stakingValidator by StakingValidatorCacheTable.stakingValidator
    override var lastHit by StakingValidatorCacheTable.lastHit
    override var hitCount by StakingValidatorCacheTable.hitCount
}



object ValidatorDelegationCacheTable : CacheIdTable<String>(name = "validator_delegations_cache") {
    val operatorAddress = reference("operator_address", ValidatorAddressesTable.operatorAddress).primaryKey()
    override val id = operatorAddress.entityId()
    val validatorDelegations = jsonb<ValidatorDelegationCacheTable, PbDelegations>("validator_delegations", OBJECT_MAPPER)
}

class ValidatorDelegationCacheRecord(id: EntityID<String>) : CacheEntity<String>(id) {
    companion object : CacheEntityClass<String, ValidatorDelegationCacheRecord>(ValidatorDelegationCacheTable) {
        fun insertIgnore(operatorAddress: String, json: PbDelegations) =
            transaction {
                ValidatorDelegationCacheTable.insertIgnore {
                    it[this.operatorAddress] = operatorAddress
                    it[this.validatorDelegations] = json
                    it[this.hitCount] = 0
                    it[this.lastHit] = DateTime.now()
                }.let { json }
            }
    }

    var operatorAddress by ValidatorDelegationCacheTable.operatorAddress
    var validatorDelegations by ValidatorDelegationCacheTable.validatorDelegations
    override var lastHit by ValidatorDelegationCacheTable.lastHit
    override var hitCount by ValidatorDelegationCacheTable.hitCount
}


data class ValidatorAddresses(val consensusAddress: String, val consensusPubKeyAddress: String, val operatorAddress: String)
