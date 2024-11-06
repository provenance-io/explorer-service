package io.provenance.explorer.domain.entities

import io.provenance.explorer.domain.core.sql.toProcedureObject
import io.provenance.explorer.domain.extensions.execAndMap
import io.provenance.explorer.domain.extensions.toDateTime
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.sql.ResultSet

object NavEventsTable : IdTable<Int>(name = "nav_events") {
    val blockHeight = integer("block_height")
    val blockTime = datetime("block_time")
    val chainId = integer("chain_id")
    val txHash = text("tx_hash")
    val eventOrder = integer("event_order")
    val eventType = text("event_type")
    val scopeId = text("scope_id").nullable()
    val denom = text("denom").nullable()
    val priceAmount = long("price_amount").nullable()
    val priceDenom = text("price_denom").nullable()
    val volume = long("volume")
    val dataSource = text("source")

    override val id = blockHeight.entityId()

    init {
        uniqueIndex("nav_events_unique_idx", blockHeight, chainId, txHash, eventOrder)
    }
}

class NavEventsRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<NavEventsRecord>(NavEventsTable) {
        fun insert(
            blockHeight: Int,
            blockTime: DateTime,
            chainId: Int,
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
                it[this.chainId] = chainId
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
    }

    var blockHeight by NavEventsTable.blockHeight
    var blockTime by NavEventsTable.blockTime
    var chainId by NavEventsTable.chainId
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
    val blockTime: DateTime,
    val chainId: Int,
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
        rs.getTimestamp("block_time").toDateTime(),
        rs.getInt("chain_id"),
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
        record.chainId,
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
    val blockTime: DateTime,
    val chainId: Int,
    val txHash: String,
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
        record.chainId,
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