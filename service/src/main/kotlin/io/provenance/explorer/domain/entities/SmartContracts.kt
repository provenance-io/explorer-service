package io.provenance.explorer.domain.entities

import cosmwasm.wasm.v1.QueryOuterClass
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.extensions.nullOrString
import io.provenance.explorer.domain.extensions.toBase64
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction

object SmCodeTable : IdTable<Int>(name = "sm_code") {
    override val id = integer("id").entityId()
    override val primaryKey = PrimaryKey(id)
    val creationHeight = integer("creation_height")
    val creator = varchar("creator", 128).nullable()
    val dataHash = varchar("data_hash", 256).nullable()
    val data = jsonb<SmCodeTable, QueryOuterClass.QueryCodeResponse>("data", OBJECT_MAPPER).nullable()
}

class SmCodeRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SmCodeRecord>(SmCodeTable) {

        fun getOrInsert(codeId: Int, data: QueryOuterClass.QueryCodeResponse?, creationHeight: Int) =
            transaction {
                findById(codeId)?.apply {
                    if (this.creationHeight > creationHeight)
                        this.creationHeight = creationHeight
                    if (this.data == null && data != null) {
                        this.creator = data.codeInfo.creator
                        this.dataHash = data.codeInfo.dataHash.toBase64()
                        this.data = data
                    }
                }?.id?.value ?: SmCodeTable.insertAndGetId {
                    it[this.id] = codeId
                    it[this.creationHeight] = creationHeight
                    it[this.creator] = data?.codeInfo?.creator
                    it[this.dataHash] = data?.codeInfo?.dataHash?.toBase64()
                    it[this.data] = data
                }.value
            }
    }

    var creationHeight by SmCodeTable.creationHeight
    var creator by SmCodeTable.creator
    var dataHash by SmCodeTable.dataHash
    var data by SmCodeTable.data
}

object SmContractTable : IntIdTable(name = "sm_contract") {
    val contractAddress = varchar("contract_address", 128)
    val creationHeight = integer("creation_height")
    val codeId = integer("code_id")
    val creator = varchar("creator", 128)
    val admin = varchar("admin", 128).nullable()
    val label = text("label").nullable()
    val data = jsonb<SmContractTable, QueryOuterClass.QueryContractInfoResponse>("data", OBJECT_MAPPER)
}

class SmContractRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SmContractRecord>(SmContractTable) {

        fun findByContractAddress(contractAddr: String) = transaction {
            SmContractRecord.find { SmContractTable.contractAddress eq contractAddr }.firstOrNull()
        }

        fun getOrInsert(data: QueryOuterClass.QueryContractInfoResponse, creationHeight: Int) =
            transaction {
                findByContractAddress(data.address)?.apply {
                    if (this.creationHeight > creationHeight)
                        this.creationHeight = creationHeight
                }?.id?.value ?: SmContractTable.insertAndGetId {
                    it[this.contractAddress] = data.address
                    it[this.creationHeight] = creationHeight
                    it[this.codeId] = data.contractInfo.codeId.toInt()
                    it[this.creator] = data.contractInfo.creator
                    it[this.admin] = data.contractInfo.admin.nullOrString()
                    it[this.label] = data.contractInfo.label.nullOrString()
                    it[this.data] = data
                }.value
            }

        fun getPaginated(offset: Int, limit: Int, codeId: Int? = null) = transaction {
            SmContractRecord
                .find { if (codeId != null) SmContractTable.codeId eq codeId else Op.TRUE }
                .orderBy(Pair(SmContractTable.creationHeight, SortOrder.DESC))
                .limit(limit, offset.toLong())
                .toList()
        }
    }

    var contractAddress by SmContractTable.contractAddress
    var creationHeight by SmContractTable.creationHeight
    var codeId by SmContractTable.codeId
    var creator by SmContractTable.creator
    var admin by SmContractTable.admin
    var label by SmContractTable.label
    var data by SmContractTable.data
}
