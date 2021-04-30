package io.provenance.explorer.domain.entities

import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.marker.v1.MarkerAccount
import io.provenance.marker.v1.MarkerStatus
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.transactions.transaction


object MarkerCacheTable : IntIdTable(name = "marker_cache") {
    val markerAddress = varchar("marker_address", 128)
    val markerType = varchar("marker_type", 128)
    val denom = varchar("denom", 64)
    val status = varchar("status", 128)
    val totalSupply = decimal("total_supply", 30, 10)
    val data = jsonb<MarkerCacheTable, MarkerAccount>("data", OBJECT_MAPPER)
}

class MarkerCacheRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MarkerCacheRecord>(MarkerCacheTable) {

        fun insertIgnore(marker: MarkerAccount) =
            transaction {
                MarkerCacheTable.insertIgnoreAndGetId {
                    it[this.markerAddress] = marker.baseAccount.address
                    it[this.markerType] = marker.markerType.name
                    it[this.denom] = marker.denom
                    it[this.status] = marker.status.toString()
                    it[this.totalSupply] = marker.supply.toBigDecimal()
                    it[this.data] = marker
                }.let { Pair(it, marker) }
            }

        fun findByDenom(denom: String) = transaction {
            MarkerCacheRecord.find { MarkerCacheTable.denom eq denom }.firstOrNull()
        }

        fun findByAddress(addr: String) = transaction {
            MarkerCacheRecord.find { MarkerCacheTable.markerAddress eq addr }.firstOrNull()
        }

        fun findByStatus(status: List<MarkerStatus>) = transaction {
            MarkerCacheRecord.find { MarkerCacheTable.status inList status.map { it.name }}.toList()
        }
    }

    var markerAddress by MarkerCacheTable.markerAddress
    var markerType by MarkerCacheTable.markerType
    var denom by MarkerCacheTable.denom
    var status by MarkerCacheTable.status
    var totalSupply by MarkerCacheTable.totalSupply
    var data by MarkerCacheTable.data
}
