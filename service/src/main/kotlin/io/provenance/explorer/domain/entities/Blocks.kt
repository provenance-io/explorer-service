package io.provenance.explorer.domain.entities

import cosmos.base.tendermint.v1beta1.Query
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.DateTrunc
import io.provenance.explorer.domain.core.sql.ExtractDOW
import io.provenance.explorer.domain.core.sql.ExtractDay
import io.provenance.explorer.domain.core.sql.ExtractEpoch
import io.provenance.explorer.domain.core.sql.ExtractHour
import io.provenance.explorer.domain.core.sql.Lag
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.models.explorer.DateTruncGranularity
import io.provenance.explorer.domain.models.explorer.TxHeatmap
import io.provenance.explorer.domain.models.explorer.TxHeatmapDate
import io.provenance.explorer.domain.models.explorer.TxHeatmapHour
import io.provenance.explorer.domain.models.explorer.TxHistory
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.trim
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

        fun getDaysBetweenHeights(minHeight: Int, maxHeight: Int) = transaction {
            val dateTrunc = DateTrunc(DateTruncGranularity.DAY.name, BlockCacheTable.blockTimestamp)
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

        fun getBlocksWithTxs(count: Int, offset: Int) = transaction {
            BlockCacheRecord.find { BlockCacheTable.txCount greater 0 }.limit(count, offset.toLong())
        }

        fun getCountWithTxs() = transaction {
            BlockCacheRecord.find { BlockCacheTable.txCount greater 0 }.count()
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

object BlockProposerTable : IdTable<Int>(name = "block_proposer") {
    val blockHeight = integer("block_height")
    override val id = blockHeight.entityId()
    val proposerOperatorAddress = varchar("proposer_operator_address", 96)
    val minGasFee = double("min_gas_fee").nullable()
    val blockTimestamp = datetime("block_timestamp")
}

class BlockProposerRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BlockProposerRecord>(BlockProposerTable) {

        fun save(height: Int, minGasFee: Double?, timestamp: DateTime?, proposer: String?) = transaction {
            (
                BlockProposerRecord.findById(height) ?: new(height) {
                    this.proposerOperatorAddress = proposer!!
                    this.blockTimestamp = timestamp!!
                }
                ).apply {
                this.minGasFee = minGasFee
            }
        }

        fun findMissingRecords() = transaction {
            BlockCacheTable
                .leftJoin(BlockProposerTable, { BlockCacheTable.height }, { BlockProposerTable.blockHeight })
                .slice(BlockCacheTable.columns)
                .select { BlockProposerTable.blockHeight.isNull() }
                .let { BlockCacheRecord.wrapRows(it).toSet() }
        }

        fun findCurrentFeeForAddress(address: String) = transaction {
            BlockProposerRecord
                .find {
                    (BlockProposerTable.proposerOperatorAddress eq address) and
                        (BlockProposerTable.minGasFee.isNotNull())
                }
                .orderBy(Pair(BlockProposerTable.blockHeight, SortOrder.DESC))
                .limit(1)
                .firstOrNull()
        }

        fun findForDates(fromDate: DateTime, toDate: DateTime, address: String?) = transaction {
            val query = BlockProposerTable
                .select { BlockProposerTable.blockTimestamp.between(fromDate, toDate.plusDays(1)) }
            if (address != null)
                query.andWhere { BlockProposerTable.proposerOperatorAddress eq address }
            BlockProposerRecord.wrapRows(query)
        }
    }

    var blockHeight by BlockProposerTable.blockHeight
    var proposerOperatorAddress by BlockProposerTable.proposerOperatorAddress
    var minGasFee by BlockProposerTable.minGasFee
    var blockTimestamp by BlockProposerTable.blockTimestamp
}

object MissedBlocksTable : IntIdTable(name = "missed_blocks") {
    val blockHeight = integer("block_height")
    val valConsAddr = varchar("val_cons_address", 128)
    val runningCount = integer("running_count")
    val totalCount = integer("total_count")
}

class MissedBlocksRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MissedBlocksRecord>(MissedBlocksTable) {

        fun findLatestForVal(valconsAddr: String) = transaction {
            MissedBlocksRecord.find { MissedBlocksTable.valConsAddr eq valconsAddr }
                .orderBy(Pair(MissedBlocksTable.blockHeight, SortOrder.DESC))
                .firstOrNull()
        }

        fun findForValFirstUnderHeight(valconsAddr: String, height: Int) = transaction {
            MissedBlocksRecord
                .find { (MissedBlocksTable.valConsAddr eq valconsAddr) and (MissedBlocksTable.blockHeight lessEq height) }
                .orderBy(Pair(MissedBlocksTable.blockHeight, SortOrder.DESC))
                .firstOrNull()
        }

        fun insert(height: Int, valconsAddr: String) = transaction {
            val (running, total, updateFromHeight) = findLatestForVal(valconsAddr)?.let { rec ->
                when {
                    // If current height follows the last height, continue sequences
                    rec.blockHeight == height - 1 -> listOf(rec.runningCount, rec.totalCount, null)
                    rec.blockHeight > height ->
                        // If current height is under the found height, find the last one directly under current
                        // height, and see if it follows the sequence
                        when (val last = findForValFirstUnderHeight(valconsAddr, height - 1)) {
                            null -> listOf(0, 0, height)
                            else -> listOf(
                                if (last.blockHeight == height - 1) last.runningCount else 0,
                                last.totalCount,
                                height
                            )
                        }
                    // Restart running sequence
                    else -> listOf(0, rec.totalCount, null)
                }
            } ?: listOf(0, 0, null)

            MissedBlocksTable.insertIgnore {
                it[this.blockHeight] = height
                it[this.valConsAddr] = valconsAddr
                it[this.runningCount] = running!! + 1
                it[this.totalCount] = total!! + 1
            }

            // Update following height records
            if (updateFromHeight != null)
                updateRecords(updateFromHeight, valconsAddr, running!! + 1, total!! + 1)
        }

        fun updateRecords(height: Int, valconsAddr: String, currRunning: Int, currTotal: Int) = transaction {
            val records = MissedBlocksRecord
                .find { (MissedBlocksTable.valConsAddr eq valconsAddr) and (MissedBlocksTable.blockHeight greater height) }
                .orderBy(Pair(MissedBlocksTable.blockHeight, SortOrder.ASC))

            BatchUpdateStatement(MissedBlocksTable).apply {
                var lastHeight = height
                var lastRunning = currRunning
                var lastTotal = currTotal
                records.forEach {
                    addBatch(it.id)
                    val running = if (lastHeight == it.blockHeight - 1) lastRunning + 1 else 1
                    this[MissedBlocksTable.runningCount] = running
                    this[MissedBlocksTable.totalCount] = lastTotal + 1
                    lastHeight = it.blockHeight
                    lastRunning = running
                    lastTotal += 1
                }
                execute(TransactionManager.current())
            }
        }
    }

    var blockHeight by MissedBlocksTable.blockHeight
    var valConsAddr by MissedBlocksTable.valConsAddr
    var runningCount by MissedBlocksTable.runningCount
    var totalCount by MissedBlocksTable.totalCount
}

object BlockCacheHourlyTxCountsTable : IdTable<DateTime>(name = "block_cache_hourly_tx_counts") {
    val blockTimestamp = datetime("block_timestamp")
    val txCount = integer("tx_count")
    override val id = blockTimestamp.entityId()
}

class BlockCacheHourlyTxCountsRecord(id: EntityID<DateTime>) : Entity<DateTime>(id) {
    companion object : EntityClass<DateTime, BlockCacheHourlyTxCountsRecord>(BlockCacheHourlyTxCountsTable) {

        fun getTxCountsForParams(fromDate: DateTime, toDate: DateTime, granularity: String) = transaction {
            if ("HOUR" == granularity) {
                getHourlyCounts(fromDate, toDate)
            } else {
                getDailyCounts(fromDate, toDate)
            }
        }

        fun getTxHeatmap() = transaction {
            val blockTimestamp = BlockCacheHourlyTxCountsTable.blockTimestamp
            val dateTrunc = DateTrunc("DAY", blockTimestamp)
            val dow = ExtractDOW(blockTimestamp)
            val day = ExtractDay(blockTimestamp)
            val hour = ExtractHour(blockTimestamp)
            val txSum = BlockCacheHourlyTxCountsTable.txCount.sum()
            BlockCacheHourlyTxCountsTable
                .slice(dateTrunc, dow, day, hour, txSum)
                .selectAll()
                .groupBy(dateTrunc, dow, day, hour)
                .orderBy(dateTrunc, SortOrder.ASC)
                .map {
                    TxHeatmapDate(
                        it[dateTrunc]!!.withZone(DateTimeZone.UTC).toString("yyyy-MM-dd"),
                        it[dow],
                        it[day].trim(),
                        it[hour],
                        it[txSum]!!
                    )
                }
                .groupBy { it.date }
                .map {
                    TxHeatmap(
                        date = it.key,
                        dow = it.value[0].dow,
                        day = it.value[0].day,
                        data = it.value.map {
                            TxHeatmapHour(
                                it.hour,
                                it.numberTxs
                            )
                        }
                    )
                }
        }

        private fun getDailyCounts(fromDate: DateTime, toDate: DateTime) = transaction {
            val blockTimestamp = BlockCacheHourlyTxCountsTable.blockTimestamp
            val dateTrunc = DateTrunc("DAY", blockTimestamp)
            val txSum = BlockCacheHourlyTxCountsTable.txCount.sum()
            BlockCacheHourlyTxCountsTable.slice(dateTrunc, txSum)
                .select {
                    dateTrunc.between(fromDate.startOfDay(), toDate.startOfDay().plusDays(1))
                }
                .groupBy(dateTrunc)
                .orderBy(dateTrunc, SortOrder.DESC)
                .map {
                    TxHistory(
                        it[dateTrunc]!!.withZone(DateTimeZone.UTC).toString("yyyy-MM-dd HH:mm:ss"),
                        it[txSum]!!
                    )
                }
        }

        private fun getHourlyCounts(fromDate: DateTime, toDate: DateTime) = transaction {
            val blockTimestamp = BlockCacheHourlyTxCountsTable.blockTimestamp
            val txCount = BlockCacheHourlyTxCountsTable.txCount
            BlockCacheHourlyTxCountsTable.slice(blockTimestamp, txCount)
                .select {
                    blockTimestamp.between(fromDate.startOfDay(), toDate.startOfDay().plusDays(1))
                }
                .orderBy(blockTimestamp, SortOrder.DESC)
                .map {
                    TxHistory(
                        it[blockTimestamp].withZone(DateTimeZone.UTC).toString("yyyy-MM-dd HH:mm:ss"),
                        it[txCount]
                    )
                }
        }

        fun updateTxCounts(blockTimestamp: DateTime) = transaction {
            val blockTimestampUtc = blockTimestamp.withZone(DateTimeZone.UTC).toString("yyyy-MM-dd HH:mm:ss")
            val conn = TransactionManager.current().connection
            val queries = listOf("CALL update_block_cache_hourly_tx_counts('$blockTimestampUtc'::TIMESTAMP)")
            conn.executeInBatch(queries)
        }
    }

    var blockTimestamp by BlockCacheHourlyTxCountsTable.blockTimestamp
    var txCount by BlockCacheHourlyTxCountsTable.txCount
}
