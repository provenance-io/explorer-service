package io.provenance.explorer.domain.entities

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction


object NftScopeTable : IntIdTable(name = "nft_scope") {
    val uuid = varchar("uuid", 128)
    val address = varchar("address", 128)
    val deleted = bool("deleted").default(false)
}

class NftScopeRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<NftScopeRecord>(NftScopeTable) {

        fun findByUuid(uuid: String) = transaction {
            NftScopeRecord.find { NftScopeTable.uuid eq uuid }.firstOrNull()
        }

        fun getOrInsert(uuid: String, address: String) =
            transaction {
                findByUuid(uuid) ?: NftScopeTable.insertAndGetId {
                    it[this.uuid] = uuid
                    it[this.address] = address
                }.let { findById(it)!! }
            }

        fun markDeleted(uuid: String, address: String) = transaction {
            (findByUuid(uuid) ?: getOrInsert(uuid, address)).apply { this.deleted = true }
        }
    }

    var uuid by NftScopeTable.uuid
    var address by NftScopeTable.address
    var deleted by NftScopeTable.deleted
}

object NftScopeSpecTable : IntIdTable(name = "nft_scope_spec") {
    val uuid = varchar("uuid", 128)
    val address = varchar("address", 128)
    val deleted = bool("deleted").default(false)
}

class NftScopeSpecRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<NftScopeSpecRecord>(NftScopeSpecTable) {

        fun findByUuid(uuid: String) = transaction {
            NftScopeSpecRecord.find { NftScopeSpecTable.uuid eq uuid }.firstOrNull()
        }

        fun getOrInsert(uuid: String, address: String) =
            transaction {
                findByUuid(uuid) ?: NftScopeSpecTable.insertAndGetId {
                    it[this.uuid] = uuid
                    it[this.address] = address
                }.let { findById(it)!! }
            }

        fun markDeleted(uuid: String, address: String) = transaction {
            (findByUuid(uuid) ?: getOrInsert(uuid, address)).apply { this.deleted = true }
        }
    }

    var uuid by NftScopeSpecTable.uuid
    var address by NftScopeSpecTable.address
    var deleted by NftScopeSpecTable.deleted
}

object NftContractSpecTable : IntIdTable(name = "nft_contract_spec") {
    val uuid = varchar("uuid", 128)
    val address = varchar("address", 128)
    val deleted = bool("deleted").default(false)
}

class NftContractSpecRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<NftContractSpecRecord>(NftContractSpecTable) {

        fun findByUuid(uuid: String) = transaction {
            NftContractSpecRecord.find { NftContractSpecTable.uuid eq uuid }.firstOrNull()
        }

        fun getOrInsert(uuid: String, address: String) =
            transaction {
                findByUuid(uuid) ?: NftContractSpecTable.insertAndGetId {
                    it[this.uuid] = uuid
                    it[this.address] = address
                }.let { findById(it)!! }
            }

        fun markDeleted(uuid: String, address: String) = transaction {
            (findByUuid(uuid) ?: getOrInsert(uuid, address)).apply { this.deleted = true }
        }
    }

    var uuid by NftContractSpecTable.uuid
    var address by NftContractSpecTable.address
    var deleted by NftContractSpecTable.deleted
}
