package io.provenance.explorer.domain.entities

import cosmos.base.tendermint.v1beta1.Query
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.ExtractEpoch
import io.provenance.explorer.domain.core.sql.Lag
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.models.explorer.TxHistory
import io.provenance.explorer.domain.models.explorer.DateTruncGranularity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.sql.DecimalColumnType
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.append
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.jodatime.CustomDateTimeFunction
import org.jetbrains.exposed.sql.jodatime.DateColumnType
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.math.BigDecimal

object BlockCacheTable : CacheIdTable<Int>(name = "block_cache") {
    val height = integer("height")
    override val id = height.entityId()
    val block = jsonb<BlockCacheTable, Query.GetBlockByHeightResponse>("block", OBJECT_MAPPER)
    val blockTimestamp = datetime("block_timestamp")
    val txCount = integer("tx_count")
}

class BlockCacheRecord(id: EntityID<Int>) : CacheEntity<Int>(id) {
    companion object : CacheEntityClass<Int, BlockCacheRecord>(BlockCacheTable) {

        fun insertIgnore(
            blockHeight: Int,
            transactionCount: Int,
            timestamp: DateTime,
            blockMeta: Query.GetBlockByHeightResponse
        ) =
            transaction {
                BlockCacheTable.insertIgnore {
                    it[this.height] = blockHeight
                    it[this.block] = blockMeta
                    it[this.txCount] = transactionCount
                    it[this.blockTimestamp] = timestamp
                    it[this.hitCount] = 0
                    it[this.lastHit] = DateTime.now()
                }.let { blockMeta }
            }

        private fun String.dateTruncFunc() =
            CustomDateTimeFunction("DATE_TRUNC", stringLiteral(this),  BlockCacheTable.blockTimestamp)

        fun getTxCountsForParams(fromDate: DateTime, toDate: DateTime, granularity: String) = transaction {
            val dateTrunc = granularity.dateTruncFunc()
            val txSum = BlockCacheTable.txCount.sum()
            BlockCacheTable.slice(dateTrunc, txSum)
                .select { BlockCacheTable.blockTimestamp.between(fromDate, toDate.plusDays(1)) }
                .groupBy(dateTrunc)
                .orderBy(dateTrunc, SortOrder.DESC)
                .map {
                    TxHistory(
                        it[dateTrunc]!!.withZone(DateTimeZone.UTC)
                            .toString("yyyy-MM-dd HH:mm:ss"),
                        it[txSum]!!) }
        }

        fun getDaysBetweenHeights(minHeight: Int, maxHeight: Int) = transaction {
            val dateTrunc = DateTruncGranularity.DAY.name.dateTruncFunc()
            BlockCacheTable.slice(dateTrunc)
                .select { BlockCacheTable.height.between(minHeight, maxHeight) }
                .groupBy(dateTrunc)
                .orderBy(dateTrunc, SortOrder.DESC)
                .toList()
                .size
        }

        fun getBlockCreationInterval(limit: Int): List<Pair<Int, BigDecimal?>> = transaction {
            val lag = Lag(BlockCacheTable.blockTimestamp, BlockCacheTable.height)
            val lagExtract = ExtractEpoch(lag)
            val baseExtract = ExtractEpoch(BlockCacheTable.blockTimestamp)
            val creationTime = lagExtract.minus(baseExtract)

            BlockCacheTable.slice(BlockCacheTable.height, creationTime)
                .selectAll()
                .orderBy(BlockCacheTable.height, SortOrder.DESC)
                .limit(limit)
                .map { Pair(it[BlockCacheTable.height], it[creationTime]) }
        }
    }

    var height by BlockCacheTable.height
    var block by BlockCacheTable.block
    var blockTimestamp by BlockCacheTable.blockTimestamp
    var txCount by BlockCacheTable.txCount
    override var lastHit by BlockCacheTable.lastHit
    override var hitCount by BlockCacheTable.hitCount
}

object BlockIndexTable : IdTable<Int>(name = "block_index") {
    override val id = integer("id").entityId()
    val maxHeightRead = integer("max_height_read").nullable()
    val minHeightRead = integer("min_height_read").nullable()
    val lastUpdate = datetime("last_update")
}

class BlockIndexRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BlockIndexRecord>(BlockIndexTable) {
        fun getIndex() = transaction {
            BlockIndexRecord.findById(1)
        }

        fun save(maxHeight: Int?, minHeight: Int?) = transaction {
            (getIndex() ?: new(1) {}).apply {
                if (maxHeight != null) this.maxHeightRead = maxHeight
                if (minHeight != null) this.minHeightRead = minHeight
                this.lastUpdate = DateTime.now()
            }
        }
    }

    var maxHeightRead by BlockIndexTable.maxHeightRead
    var minHeightRead by BlockIndexTable.minHeightRead
    var lastUpdate by BlockIndexTable.lastUpdate
}
