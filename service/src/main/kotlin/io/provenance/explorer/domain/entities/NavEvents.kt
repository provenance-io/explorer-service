package io.provenance.explorer.domain.entities

import io.provenance.explorer.domain.extensions.execAndMap
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.javatime.JavaLocalDateTimeColumnType
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet
import java.time.LocalDateTime

object NavEventsTable : IntIdTable(name = "nav_events") {
    val blockHeight = integer("block_height")
    val blockTime = datetime("block_time")
    val txHash = text("tx_hash")
    val eventOrder = integer("event_order")
    val eventType = text("event_type")
    val scopeId = text("scope_id").nullable()
    val denom = text("denom").nullable()
    val priceAmount = long("price_amount").nullable()
    val priceDenom = text("price_denom").nullable()
    val volume = long("volume")
    val dataSource = text("source")

    init {
        uniqueIndex("nav_events_unique_idx", blockHeight, txHash, eventOrder)
    }
}

class NavEventsRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<NavEventsRecord>(NavEventsTable) {
        fun insert(
            blockHeight: Int,
            blockTime: LocalDateTime,
            txHash: String,
            eventOrder: Int,
            eventType: String,
            scopeId: String?,
            denom: String?,
            priceAmount: Long?,
            priceDenom: String?,
            volume: Long,
            source: String
        ) = transaction {
            NavEventsTable.insertIgnore {
                it[this.blockHeight] = blockHeight
                it[this.blockTime] = blockTime
                it[this.txHash] = txHash
                it[this.eventOrder] = eventOrder
                it[this.eventType] = eventType
                it[this.scopeId] = scopeId
                it[this.denom] = denom
                it[this.priceAmount] = priceAmount
                it[this.priceDenom] = priceDenom
                it[this.volume] = volume
                it[this.dataSource] = source
            }
        }

        fun getNavEvents(
            denom: String? = null,
            scopeId: String? = null,
            fromDate: LocalDateTime? = null,
            toDate: LocalDateTime? = null,
            priceDenoms: List<String>? = null,
            source: String? = null
        ) = transaction {
            var query = """
            SELECT block_height, block_time, tx_hash, event_order, 
            event_type, scope_id, denom, price_amount, price_denom, volume, source
            FROM nav_events
            WHERE 1=1
            """.trimIndent()

            val args = mutableListOf<Pair<ColumnType, *>>()

            denom?.let {
                query += " AND denom = ?"
                args.add(Pair(VarCharColumnType(), it))
            } ?: scopeId?.let {
                query += " AND scope_id = ?"
                args.add(Pair(VarCharColumnType(), it))
            }

            fromDate?.let {
                query += " AND block_time >= ?"
                args.add(Pair(JavaLocalDateTimeColumnType(), it))
            }

            toDate?.let {
                query += " AND block_time <= ?"
                args.add(Pair(JavaLocalDateTimeColumnType(), it))
            }

            priceDenoms?.let {
                if (it.isNotEmpty()) {
                    val placeholders = it.joinToString(", ") { "?" }
                    query += " AND price_denom IN ($placeholders)"
                    it.forEach { denom ->
                        args.add(Pair(VarCharColumnType(), denom))
                    }
                }
            }

            source?.let {
                query += " AND source = ?"
                args.add(Pair(VarCharColumnType(), it))
            }

            query += " ORDER BY block_height DESC, event_order DESC"

            query.execAndMap(args) {
                NavEvent(
                    it.getInt("block_height"),
                    it.getTimestamp("block_time").toLocalDateTime(),
                    it.getString("tx_hash"),
                    it.getInt("event_order"),
                    it.getString("event_type"),
                    it.getString("scope_id"),
                    it.getString("denom"),
                    it.getLong("price_amount"),
                    it.getString("price_denom"),
                    it.getLong("volume"),
                    it.getString("source")
                )
            }
        }

        fun getLatestNavEvents(
            priceDenom: String,
            includeMarkers: Boolean,
            includeScopes: Boolean,
            fromDate: LocalDateTime? = null
        ) = transaction {
            require(includeMarkers || includeScopes) { "Either includeMarkers or includeScope must be true" }

            var query = """
            SELECT DISTINCT ON (denom, scope_id)
                block_height, block_time, tx_hash, event_order, event_type, 
                scope_id, denom, price_amount, price_denom, volume, source
            FROM nav_events
            WHERE price_denom = ?
            """.trimIndent()

            val args = mutableListOf<Pair<ColumnType, *>>(
                Pair(VarCharColumnType(), priceDenom)
            )

            fromDate?.let {
                query += " AND block_time >= ?"
                args.add(Pair(JavaLocalDateTimeColumnType(), it))
            }

            when {
                includeMarkers && includeScopes -> query += " AND (denom IS NOT NULL OR scope_id IS NOT NULL)"
                includeMarkers -> query += " AND denom IS NOT NULL"
                includeScopes -> query += " AND scope_id IS NOT NULL"
            }

            query += " ORDER BY denom, scope_id, block_height DESC, event_order DESC"

            query.execAndMap(args) {
                NavEvent(
                    it.getInt("block_height"),
                    it.getTimestamp("block_time").toLocalDateTime(),
                    it.getString("tx_hash"),
                    it.getInt("event_order"),
                    it.getString("event_type"),
                    it.getString("scope_id"),
                    it.getString("denom"),
                    it.getLong("price_amount"),
                    it.getString("price_denom"),
                    it.getLong("volume"),
                    it.getString("source")
                )
            }
        }
    }

    var blockHeight by NavEventsTable.blockHeight
    var blockTime by NavEventsTable.blockTime
    var txHash by NavEventsTable.txHash
    var eventOrder by NavEventsTable.eventOrder
    var eventType by NavEventsTable.eventType
    var scopeId by NavEventsTable.scopeId
    var denom by NavEventsTable.denom
    var priceAmount by NavEventsTable.priceAmount
    var priceDenom by NavEventsTable.priceDenom
    var volume by NavEventsTable.volume
    var source by NavEventsTable.dataSource
}

data class NavPrice(
    val blockHeight: Int,
    val blockTime: LocalDateTime,
    val txHash: String,
    val eventOrder: Int,
    val eventType: String,
    val scopeId: String?,
    val denom: String,
    val priceAmount: Long,
    val priceDenom: String,
    val volume: Long,
    val source: String
) {
    constructor(rs: ResultSet) : this(
        rs.getInt("block_height"),
        rs.getTimestamp("block_time").toLocalDateTime(),
        rs.getString("tx_hash"),
        rs.getInt("event_order"),
        rs.getString("event_type"),
        rs.getString("scope_id"),
        rs.getString("denom"),
        rs.getLong("price_amount"),
        rs.getString("price_denom"),
        rs.getLong("volume"),
        rs.getString("source")
    )

    constructor(record: NavEventsRecord) : this(
        record.blockHeight,
        record.blockTime,
        record.txHash,
        record.eventOrder,
        record.eventType,
        record.scopeId,
        record.denom!!,
        record.priceAmount!!,
        record.priceDenom!!,
        record.volume,
        record.source
    )
}

data class NavEvent(
    val blockHeight: Int,
    val blockTime: LocalDateTime,
    val txHash: String?,
    val eventOrder: Int,
    val eventType: String,
    val scopeId: String?,
    val denom: String?,
    val priceAmount: Long?,
    val priceDenom: String?,
    val volume: Long,
    val source: String
) {
    constructor(record: NavEventsRecord) : this(
        record.blockHeight,
        record.blockTime,
        record.txHash,
        record.eventOrder,
        record.eventType,
        record.scopeId,
        record.denom,
        record.priceAmount,
        record.priceDenom,
        record.volume,
        record.source
    )
}
