package io.provenance.explorer.domain.entities

import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.core.sql.nullsLast
import io.provenance.marker.v1.MarkerAccount
import io.provenance.marker.v1.MarkerStatus
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.math.BigDecimal


object MarkerCacheTable : IntIdTable(name = "marker_cache") {
    val markerAddress = varchar("marker_address", 128).nullable()
    val markerType = varchar("marker_type", 128)
    val denom = varchar("denom", 256)
    val status = varchar("status", 128)
    val supply = decimal("supply", 100,10)
    val lastTx = datetime("last_tx_timestamp").nullable()
    val data = jsonb<MarkerCacheTable, MarkerAccount>("data", OBJECT_MAPPER).nullable()
}

enum class BaseDenomType { DENOM, IBC_DENOM }

class MarkerCacheRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MarkerCacheRecord>(MarkerCacheTable) {

        fun insertIgnore(
            addr: String?,
            type: String,
            denom: String,
            status: String,
            marker: MarkerAccount?,
            supply: BigDecimal,
            txTimestamp: DateTime?
        ) =
            transaction {
                MarkerCacheTable.insertIgnoreAndGetId {
                    it[this.markerAddress] = addr
                    it[this.markerType] = type
                    it[this.denom] = denom
                    it[this.status] = status
                    it[this.supply] = supply
                    it[this.lastTx] = txTimestamp
                    it[this.data] = marker
                }.let { Pair(it, findById(it!!)!!) }
            }

        fun findByDenom(denom: String) = transaction {
            MarkerCacheRecord.find { MarkerCacheTable.denom eq denom }.firstOrNull()
        }

        fun findByAddress(addr: String) = transaction {
            MarkerCacheRecord.find { MarkerCacheTable.markerAddress eq addr }.firstOrNull()
        }

        fun findByStatusPaginated(status: List<MarkerStatus>, offset: Int, limit: Int) = transaction {
            MarkerCacheTable.select { MarkerCacheTable.status inList status.map { it.name } }
                .andWhere { notIbcExpr }
                .orderBy(MarkerCacheTable.lastTx.nullsLast(), SortOrder.DESC)
                .orderBy(MarkerCacheTable.lastTx, SortOrder.DESC)
                .orderBy(MarkerCacheTable.supply, SortOrder.DESC)
                .orderBy(MarkerCacheTable.denom, SortOrder.ASC)
                .limit(limit, offset.toLong())
                .let { MarkerCacheRecord.wrapRows(it).toList() }
        }

        fun findCountByStatus(status: List<MarkerStatus>) = transaction {
            MarkerCacheRecord.find { (MarkerCacheTable.status inList status.map { it.name }) and notIbcExpr }.count()
        }

        private val notIbcExpr: Op<Boolean> = MarkerCacheTable.markerType neq BaseDenomType.IBC_DENOM.name

        fun findIbcPaginated(offset: Int, limit: Int) = transaction {
            MarkerCacheTable.select { MarkerCacheTable.markerType eq BaseDenomType.IBC_DENOM.name }
                .orderBy(MarkerCacheTable.lastTx.nullsLast(), SortOrder.DESC)
                .orderBy(MarkerCacheTable.lastTx, SortOrder.DESC)
                .orderBy(MarkerCacheTable.supply, SortOrder.DESC)
                .orderBy(MarkerCacheTable.denom, SortOrder.ASC)
                .limit(limit, offset.toLong())
                .let { MarkerCacheRecord.wrapRows(it).toList() }
        }

        fun findCountByIbc() = transaction {
            MarkerCacheRecord.find { MarkerCacheTable.markerType eq BaseDenomType.IBC_DENOM.name }.count()
        }
    }

    var markerAddress by MarkerCacheTable.markerAddress
    var markerType by MarkerCacheTable.markerType
    var denom by MarkerCacheTable.denom
    var status by MarkerCacheTable.status
    var supply by MarkerCacheTable.supply
    var lastTx by MarkerCacheTable.lastTx
    var data by MarkerCacheTable.data
}
