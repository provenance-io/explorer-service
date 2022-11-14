package io.provenance.explorer.domain.entities

import cosmos.base.tendermint.v1beta1.Query
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.DateTrunc
import io.provenance.explorer.domain.core.sql.Distinct
import io.provenance.explorer.domain.core.sql.ExtractDOW
import io.provenance.explorer.domain.core.sql.ExtractDay
import io.provenance.explorer.domain.core.sql.ExtractHour
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.core.sql.toProcedureObject
import io.provenance.explorer.domain.extensions.average
import io.provenance.explorer.domain.extensions.exec
import io.provenance.explorer.domain.extensions.execAndMap
import io.provenance.explorer.domain.extensions.map
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.models.explorer.BlockProposer
import io.provenance.explorer.domain.models.explorer.BlockUpdate
import io.provenance.explorer.domain.models.explorer.DateTruncGranularity
import io.provenance.explorer.domain.models.explorer.DateTruncGranularity.DAY
import io.provenance.explorer.domain.models.explorer.DateTruncGranularity.HOUR
import io.provenance.explorer.domain.models.explorer.DateTruncGranularity.MINUTE
import io.provenance.explorer.domain.models.explorer.DateTruncGranularity.MONTH
import io.provenance.explorer.domain.models.explorer.MissedBlockPeriod
import io.provenance.explorer.domain.models.explorer.TxHeatmap
import io.provenance.explorer.domain.models.explorer.TxHeatmapDay
import io.provenance.explorer.domain.models.explorer.TxHeatmapHour
import io.provenance.explorer.domain.models.explorer.TxHeatmapRaw
import io.provenance.explorer.domain.models.explorer.TxHeatmapRes
import io.provenance.explorer.domain.models.explorer.TxHistory
import io.provenance.explorer.domain.models.explorer.ValidatorMoniker
import io.provenance.explorer.domain.models.explorer.toProcedureObject
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.Max
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Sum
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.jodatime.DateColumnType
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

object BlockCacheTable : CacheIdTable<Int>(name = "block_cache") {
    val height = integer("height")
    override val id = height.entityId()
    val block = jsonb<BlockCacheTable, Query.GetBlockByHeightResponse>("block", OBJECT_MAPPER)
    val blockTimestamp = datetime("block_timestamp")
    val txCount = integer("tx_count")
}

class BlockCacheRecord(id: EntityID<Int>) : CacheEntity<Int>(id) {
    companion object : CacheEntityClass<Int, BlockCacheRecord>(BlockCacheTable) {

        fun insertToProcedure(blockUpdate: BlockUpdate) = transaction {
            val blockStr = blockUpdate.toProcedureObject()
            val query = "CALL add_block($blockStr)"
            this.exec(query)
        }

        fun buildInsert(
            blockHeight: Int,
            transactionCount: Int,
            timestamp: DateTime,
            blockMeta: Query.GetBlockByHeightResponse
        ) =
            listOf(blockHeight, transactionCount, timestamp, blockMeta, DateTime.now(), 0).toProcedureObject()

        fun getDaysBetweenHeights(minHeight: Int, maxHeight: Int) = transaction {
            val dateTrunc =
                Distinct(
                    DateTrunc(DAY.name, BlockCacheTable.blockTimestamp),
                    DateColumnType(true)
                ).count()
            BlockCacheTable.slice(dateTrunc)
                .select { BlockCacheTable.height.between(minHeight, maxHeight) }
                .first()[dateTrunc].toInt()
        }

        fun getMaxBlockHeightOrNull() = transaction {
            val maxHeight = Max(BlockCacheTable.height, IntegerColumnType())
            BlockCacheTable.slice(maxHeight).selectAll().firstOrNull()?.let { it[maxHeight] }
        }

        fun getMaxBlockHeight() = transaction {
            val maxHeight = Max(BlockCacheTable.height, IntegerColumnType())
            BlockCacheTable.slice(maxHeight).selectAll().first().let { it[maxHeight]!! }
        }

        fun getFirstBlockAfterTime(time: DateTime) = transaction {
            BlockCacheRecord.find { BlockCacheTable.blockTimestamp.greaterEq(time) }
                .orderBy(BlockCacheTable.height to SortOrder.ASC)
                .first()
        }

        fun getLastBlockBeforeTime(time: DateTime?) = transaction {
            val query = "SELECT get_last_block_before_timestamp(?);"
            val arguments = listOf(Pair(DateColumnType(true), time))
            query.execAndMap(arguments) { it.getInt("get_last_block_before_timestamp") }.first()
        }

        fun getBlocksForRange(from: Int, to: Int) = transaction {
            BlockCacheRecord.find { BlockCacheTable.height.between(from, to) }
                .orderBy(Pair(BlockCacheTable.height, SortOrder.ASC))
                .toList()
        }
    }

    var height by BlockCacheTable.height
    var block by BlockCacheTable.block
    var blockTimestamp by BlockCacheTable.blockTimestamp
    var txCount by BlockCacheTable.txCount
    override var lastHit by BlockCacheTable.lastHit
    override var hitCount by BlockCacheTable.hitCount
}

object BlockIndexTable : IntIdTable(name = "block_index") {
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
    val blockTimestamp = datetime("block_timestamp")
    val blockLatency = decimal("block_latency", 50, 25).nullable()
}

fun BlockProposer.buildInsert() = listOf(
    this.blockHeight,
    this.proposerOperatorAddress,
    this.blockTimestamp,
    this.blockLatency
).toProcedureObject()

class BlockProposerRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BlockProposerRecord>(BlockProposerTable) {

        fun buildInsert(height: Int, minGasFee: Double, timestamp: DateTime, proposer: String) =
            listOf(height, proposer, minGasFee, timestamp, null).toProcedureObject()

        fun calcLatency() = transaction {
            val query = "CALL update_block_latency();"
            this.exec(query)
        }

        fun findAvgBlockCreation(limit: Int) = transaction {
            BlockProposerTable.slice(BlockProposerTable.blockLatency)
                .select { BlockProposerTable.blockLatency.isNotNull() }
                .orderBy(BlockProposerTable.blockHeight, SortOrder.DESC)
                .limit(limit)
                .mapNotNull { it[BlockProposerTable.blockLatency] }
                .average()
        }

        fun findMissingRecords(min: Int, max: Int, limit: Int) = transaction {
            BlockCacheTable
                .leftJoin(BlockProposerTable, { BlockCacheTable.height }, { BlockProposerTable.blockHeight })
                .slice(BlockCacheTable.columns)
                .select { (BlockProposerTable.blockHeight.isNull()) and (BlockCacheTable.height.between(min, max)) }
                .orderBy(BlockCacheTable.height, SortOrder.ASC)
                .limit(limit)
                .let { BlockCacheRecord.wrapRows(it).toSet() }
        }

        fun getRecordsForProposer(address: String, limit: Int) = transaction {
            BlockProposerRecord.find {
                (BlockProposerTable.proposerOperatorAddress eq address) and
                    (BlockProposerTable.blockLatency.isNotNull())
            }.orderBy(Pair(BlockProposerTable.blockHeight, SortOrder.DESC))
                .limit(limit)
                .toList()
        }
    }

    var blockHeight by BlockProposerTable.blockHeight
    var proposerOperatorAddress by BlockProposerTable.proposerOperatorAddress
    var blockTimestamp by BlockProposerTable.blockTimestamp
    var blockLatency by BlockProposerTable.blockLatency
}

object MissedBlocksTable : IntIdTable(name = "missed_blocks") {
    val blockHeight = integer("block_height")
    val valConsAddr = varchar("val_cons_address", 128)
    val runningCount = integer("running_count")
    val totalCount = integer("total_count")
}

class MissedBlocksRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MissedBlocksRecord>(MissedBlocksTable) {

        fun findValidatorsWithMissedBlocksForPeriod(fromHeight: Int, toHeight: Int, valConsAddr: String?) =
            transaction {
                val query = "SELECT * FROM missed_block_periods(?, ?, ?) "
                val arguments = mutableListOf<Pair<ColumnType, *>>(
                    Pair(IntegerColumnType(), fromHeight),
                    Pair(IntegerColumnType(), toHeight),
                    Pair(VarCharColumnType(128), valConsAddr)
                )
                query.exec(arguments).map {
                    if (it.getString("val_cons_address") != null)
                        MissedBlockPeriod(
                            ValidatorMoniker(it.getString("val_cons_address"), null, null, null),
                            (it.getArray("blocks").array as Array<out Int>).toList()
                        )
                    else null
                }.toMutableList().mapNotNull { it }
            }

        fun findDistinctValidatorsWithMissedBlocksForPeriod(fromHeight: Int, toHeight: Int) = transaction {
            val query = "SELECT distinct val_cons_address FROM missed_block_periods(?, ?, NULL);"
            val arguments = listOf(Pair(IntegerColumnType(), fromHeight), Pair(IntegerColumnType(), toHeight))

            query.exec(arguments).map { it.getString("val_cons_address") }
        }

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

        fun getTotalTxCount() = transaction {
            val txSum = Sum(BlockCacheHourlyTxCountsTable.txCount, IntegerColumnType())
            BlockCacheHourlyTxCountsTable.slice(txSum).selectAll().map { it[txSum] }.first()!!
        }

        fun getTxCountsForParams(fromDate: DateTime, toDate: DateTime, granularity: DateTruncGranularity) = transaction {
            when (granularity) {
                DAY, MONTH -> getGranularityCounts(fromDate, toDate, granularity)
                HOUR -> getHourlyCounts(fromDate, toDate)
                MINUTE -> emptyList()
            }
        }

        fun getTxHeatmap() = transaction {
            val blockTimestamp = BlockCacheHourlyTxCountsTable.blockTimestamp
            val dow = ExtractDOW(blockTimestamp)
            val day = ExtractDay(blockTimestamp)
            val hour = ExtractHour(blockTimestamp)
            val txSum = BlockCacheHourlyTxCountsTable.txCount.sum()
            val result = BlockCacheHourlyTxCountsTable
                .slice(dow, day, hour, txSum)
                .selectAll()
                .groupBy(dow)
                .groupBy(day)
                .groupBy(hour)
                .orderBy(dow, SortOrder.ASC)
                .orderBy(hour, SortOrder.ASC)
                .map {
                    TxHeatmapRaw(
                        it[dow],
                        it[day].trim(),
                        it[hour],
                        it[txSum]!!
                    )
                }
                .groupBy { it.dow }
                .map {
                    TxHeatmap(
                        dow = it.key,
                        day = it.value[0].day,
                        data = it.value.map { row ->
                            TxHeatmapHour(
                                row.hour,
                                row.numberTxs
                            )
                        }
                    )
                }

            val dayTotals = result.map { row -> TxHeatmapDay(row.day, row.data.sumOf { it.numberTxs }) }
            val hourTotals = result
                .flatMap { it.data }
                .groupBy { it.hour }
                .map { row -> TxHeatmapHour(row.key, row.value.sumOf { it.numberTxs }) }

            TxHeatmapRes(result, dayTotals, hourTotals)
        }

        private fun getGranularityCounts(fromDate: DateTime, toDate: DateTime, granularity: DateTruncGranularity) = transaction {
            val dateTrunc = DateTrunc(granularity.name, BlockCacheHourlyTxCountsTable.blockTimestamp)
            val txSum = BlockCacheHourlyTxCountsTable.txCount.sum()
            BlockCacheHourlyTxCountsTable.slice(dateTrunc, txSum)
                .select {
                    dateTrunc.between(fromDate.startOfDay(), toDate.startOfDay())
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
            BlockCacheHourlyTxCountsRecord.find {
                BlockCacheHourlyTxCountsTable.blockTimestamp.between(
                    fromDate.startOfDay(),
                    toDate.startOfDay().plusDays(1)
                )
            }.orderBy(Pair(BlockCacheHourlyTxCountsTable.blockTimestamp, SortOrder.DESC))
                .map {
                    TxHistory(
                        it.blockTimestamp.withZone(DateTimeZone.UTC).toString("yyyy-MM-dd HH:mm:ss"),
                        it.txCount
                    )
                }
        }
    }

    var blockTimestamp by BlockCacheHourlyTxCountsTable.blockTimestamp
    var txCount by BlockCacheHourlyTxCountsTable.txCount
}

object BlockTxCountsCacheTable : IntIdTable(name = "block_tx_count_cache") {
    val blockHeight = integer("block_height")
    val blockTimestamp = datetime("block_timestamp")
    val txCount = integer("tx_count").default(0)
    val processed = bool("processed").default(false)
    override val id = blockHeight.entityId()
}

class BlockTxCountsCacheRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BlockTxCountsCacheRecord>(BlockTxCountsCacheTable) {

        fun insert(height: Int, timestamp: DateTime, txCount: Int) = transaction {
            BlockTxCountsCacheTable.insertIgnore {
                it[this.blockHeight] = height
                it[this.txCount] = txCount
                it[this.blockTimestamp] = timestamp
            }
        }

        fun updateTxCounts() = transaction {
            val query = "CALL update_block_cache_hourly_tx_counts()"
            this.exec(query)
        }
    }

    var blockHeight by BlockTxCountsCacheTable.blockHeight
    var blockTimestamp by BlockTxCountsCacheTable.blockTimestamp
    var txCount by BlockTxCountsCacheTable.txCount
    var processed by BlockTxCountsCacheTable.processed
}

object BlockTxRetryTable : IdTable<Int>(name = "block_tx_retry") {
    val height = integer("height")
    val retried = bool("retried").default(false)
    val success = bool("success").default(false)
    val errorBlock = text("error_block").nullable().default(null)
    override val id = height.entityId()

    override val primaryKey = PrimaryKey(height)
}

class BlockTxRetryRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BlockTxRetryRecord>(BlockTxRetryTable) {

        fun insert(height: Int, e: Exception) = transaction {
            BlockTxRetryTable.insertIgnore {
                it[this.height] = height
                it[this.errorBlock] = e.stackTraceToString()
            }
        }

        fun insertNonRetry(height: Int, e: Exception) = transaction {
            BlockTxRetryTable.insertIgnore {
                it[this.height] = height
                it[this.retried] = true
                it[this.success] = false
                it[this.errorBlock] =
                    "NON BLOCKING ERROR: Logged to know what happened, but didnt stop processing.\n " +
                    e.stackTraceToString()
            }
        }

        fun insertNonBlockingRetry(height: Int, e: Exception) = transaction {
            BlockTxRetryTable.insertIgnore {
                it[this.height] = height
                it[this.errorBlock] =
                    "NON BLOCKING ERROR: Logged to know what happened, but didnt stop processing.\n " +
                    e.stackTraceToString()
            }
        }

        fun getRecordsToRetry() = transaction {
            BlockTxRetryRecord
                .find { (BlockTxRetryTable.retried eq false) and (BlockTxRetryTable.success eq false) }
                .orderBy(Pair(BlockTxRetryTable.height, SortOrder.ASC))
                .limit(50)
                .map { it.height }
        }

        fun updateRecord(height: Int, success: Boolean) = transaction {
            BlockTxRetryRecord.find { BlockTxRetryTable.height eq height }.first().apply {
                this.retried = true
                this.success = success
            }
        }

        fun deleteRecords(heights: List<Int>) = transaction {
            BlockTxRetryTable
                .deleteWhere {
                    (BlockTxRetryTable.retried eq true) and
                        (BlockTxRetryTable.success eq true) and
                        (BlockTxRetryTable.height inList heights)
                }
        }
    }

    var height by BlockTxRetryTable.height
    var retried by BlockTxRetryTable.retried
    var success by BlockTxRetryTable.success
    var errorBlock by BlockTxRetryTable.errorBlock
}
