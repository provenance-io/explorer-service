package io.provenance.explorer.domain.entities

import io.provenance.explorer.domain.models.explorer.NftVOTransferObj
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
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

        fun findByAddr(addr: String) = transaction {
            NftScopeRecord.find { NftScopeTable.address eq addr }.firstOrNull()
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

enum class ScopeTransferType {
    VALUE_OWNER_INITIAL,
    VALUE_OWNER_TRANSFER
}

object NftScopeVOTransferTable : IntIdTable(name = "nft_scope_value_owner_transfer") {
    val scopeId = integer("scope_id")
    val scopeAddr = varchar("scope_addr", 128)
    val address = varchar("address", 256)
    val txId = reference("tx_id", TxCacheTable)
    val blockHeight = integer("block_height")
    val txHash = varchar("tx_hash", 64)
}

class NftScopeVOTransferRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<NftScopeVOTransferRecord>(NftScopeVOTransferTable) {

        fun findByUniqueKey(scopeId: Int, address: String, txId: Int) = transaction {
            NftScopeVOTransferRecord.find {
                (NftScopeVOTransferTable.scopeId eq scopeId) and
                    (NftScopeVOTransferTable.address eq address) and
                    (NftScopeVOTransferTable.txId eq txId)
            }
                .firstOrNull()
        }

        fun getOrInsert(transferObj: NftVOTransferObj) =
            transaction {
                findByUniqueKey(
                    transferObj.scope.id.value,
                    transferObj.address,
                    transferObj.tx.id.value
                )
                    ?: NftScopeVOTransferTable.insertAndGetId {
                        it[this.scopeId] = transferObj.scope.id.value
                        it[this.scopeAddr] = transferObj.scope.address
                        it[this.address] = transferObj.address
                        it[this.txId] = transferObj.tx.id
                        it[this.blockHeight] = transferObj.tx.height
                        it[this.txHash] = transferObj.tx.hash
                    }.let { findById(it)!! }
            }

        fun findByScopeAddr(addr: String) = transaction {
            NftScopeVOTransferRecord.find { NftScopeVOTransferTable.scopeAddr eq addr }
                .orderBy(Pair(NftScopeVOTransferTable.blockHeight, SortOrder.ASC))
                .toList()
        }
    }

    var scopeId by NftScopeVOTransferTable.scopeId
    var scopeAddr by NftScopeVOTransferTable.scopeAddr
    var address by NftScopeVOTransferTable.address
    var txId by TxCacheRecord referencedOn NftScopeVOTransferTable.txId
    var blockHeight by NftScopeVOTransferTable.blockHeight
    var txHash by NftScopeVOTransferTable.txHash
}
