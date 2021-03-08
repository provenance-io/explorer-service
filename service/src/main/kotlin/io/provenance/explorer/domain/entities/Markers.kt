package io.provenance.explorer.domain.entities

import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.marker.v1.MarkerAccount
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction


object MarkerCacheTable : IdTable<String>(name = "marker_cache") {
    val markerAddress = varchar("marker_address", 128)
    override val id = markerAddress.entityId()
    val markerType = varchar("marker_type", 128)
    val denom = varchar("denom", 64)
    val status = varchar("status", 128)
    val totalSupply = decimal("total_supply", 30, 10)
    val data = jsonb<MarkerCacheTable, MarkerAccount>("data", OBJECT_MAPPER)
}

class MarkerCacheRecord(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, MarkerCacheRecord>(MarkerCacheTable) {

        fun insertIgnore(marker: MarkerAccount) =
            transaction {
                MarkerCacheTable.insertIgnore {
                    it[this.markerAddress] = marker.baseAccount.address
                    it[this.markerType] = marker.markerType.name
                    it[this.denom] = marker.denom
                    it[this.status] = marker.status.toString()
                    it[this.totalSupply] = marker.supply.toBigDecimal()
                    it[this.data] = marker
                }.let { marker }
            }
    }

    var markerAddress by MarkerCacheTable.markerAddress
    var markerType by MarkerCacheTable.markerType
    var denom by MarkerCacheTable.denom
    var status by MarkerCacheTable.status
    var totalSupply by MarkerCacheTable.totalSupply
    var data by MarkerCacheTable.data
}
