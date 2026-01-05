package io.provenance.explorer.domain.entities

import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.models.explorer.pulse.PulseCacheType
import io.provenance.explorer.domain.models.explorer.pulse.PulseMetric
import io.provenance.explorer.model.ChainAum
import io.provenance.explorer.model.ChainMarketRate
import io.provenance.explorer.model.CmcHistoricalQuote
import io.provenance.explorer.model.ValidatorMarketRate
import io.provenance.explorer.model.base.USD_UPPER
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object ValidatorMarketRateStatsTable : IntIdTable(name = "validator_market_rate_stats") {
    val date = date("date")
    val operatorAddress = varchar("operator_address", 96)
    val minMarketRate = decimal("min_market_rate", 30, 10).nullable()
    val maxMarketRate = decimal("max_market_rate", 30, 10).nullable()
    val avgMarketRate = decimal("avg_market_rate", 30, 10).nullable()

    init {
        index(true, date, operatorAddress)
    }
}

class ValidatorMarketRateStatsRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ValidatorMarketRateStatsRecord>(ValidatorMarketRateStatsTable) {

        fun save(
            address: String,
            minMarketRate: BigDecimal?,
            maxMarketRate: BigDecimal?,
            avgMarketRate: BigDecimal?,
            date: LocalDate
        ) =
            transaction {
                ValidatorMarketRateStatsTable.insertIgnore {
                    it[this.operatorAddress] = address
                    it[this.minMarketRate] = minMarketRate
                    it[this.maxMarketRate] = maxMarketRate
                    it[this.avgMarketRate] = avgMarketRate
                    it[this.date] = date
                }
            }

        fun findByAddress(address: String, fromDate: LocalDateTime?, toDate: LocalDateTime?, count: Int) = transaction {
            val query =
                ValidatorMarketRateStatsTable.select { ValidatorMarketRateStatsTable.operatorAddress eq address }
            if (fromDate != null) {
                query.andWhere { ValidatorMarketRateStatsTable.date greaterEq fromDate }
            }
            if (toDate != null) {
                query.andWhere { ValidatorMarketRateStatsTable.date lessEq toDate.plusDays(1) }
            }

            query.orderBy(ValidatorMarketRateStatsTable.date, SortOrder.ASC).limit(count)
            ValidatorMarketRateStatsRecord.wrapRows(query).map {
                ValidatorMarketRate(
                    it.operatorAddress,
                    it.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    it.minMarketRate,
                    it.maxMarketRate,
                    it.avgMarketRate
                )
            }
        }
    }

    var operatorAddress by ValidatorMarketRateStatsTable.operatorAddress
    var minMarketRate by ValidatorMarketRateStatsTable.minMarketRate
    var maxMarketRate by ValidatorMarketRateStatsTable.maxMarketRate
    var avgMarketRate by ValidatorMarketRateStatsTable.avgMarketRate
    var date by ValidatorMarketRateStatsTable.date
}

object ChainMarketRateStatsTable : IdTable<LocalDate>(name = "chain_market_rate_stats") {
    val date = date("date")
    override val id = date.entityId()
    val minMarketRate = decimal("min_market_rate", 30, 10).nullable()
    val maxMarketRate = decimal("max_market_rate", 30, 10).nullable()
    val avgMarketRate = decimal("avg_market_rate", 30, 10).nullable()
}

class ChainMarketRateStatsRecord(id: EntityID<LocalDate>) : Entity<LocalDate>(id) {
    companion object : EntityClass<LocalDate, ChainMarketRateStatsRecord>(ChainMarketRateStatsTable) {

        fun save(minMarketRate: BigDecimal?, maxMarketRate: BigDecimal?, avgMarketRate: BigDecimal?, date: LocalDate) =
            transaction {
                ChainMarketRateStatsTable.insertIgnore {
                    it[this.date] = date
                    it[this.minMarketRate] = minMarketRate
                    it[this.maxMarketRate] = maxMarketRate
                    it[this.avgMarketRate] = avgMarketRate
                }
            }

        fun findForDates(fromDate: LocalDateTime?, toDate: LocalDateTime?, count: Int) = transaction {
            val query = ChainMarketRateStatsTable.selectAll()
            if (fromDate != null) {
                query.andWhere { ChainMarketRateStatsTable.date greaterEq fromDate }
            }
            if (toDate != null) {
                query.andWhere { ChainMarketRateStatsTable.date lessEq toDate.plusDays(1) }
            }

            query.orderBy(ChainMarketRateStatsTable.date, SortOrder.ASC).limit(count)
            ChainMarketRateStatsRecord.wrapRows(query).map {
                ChainMarketRate(
                    it.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    it.minMarketRate,
                    it.maxMarketRate,
                    it.avgMarketRate
                )
            }
        }
    }

    var date by ChainMarketRateStatsTable.date
    var minMarketRate by ChainMarketRateStatsTable.minMarketRate
    var maxMarketRate by ChainMarketRateStatsTable.maxMarketRate
    var avgMarketRate by ChainMarketRateStatsTable.avgMarketRate
}

object CacheUpdateTable : IntIdTable(name = "cache_update") {
    val cacheKey = varchar("cache_key", 256)
    val description = text("description")
    val cacheValue = text("cache_value").nullable()
    val lastUpdated = datetime("last_updated")
}

enum class CacheKeys(val key: String) {
    PRICING_UPDATE("pricing_update"),
    CHAIN_RELEASES("chain_releases"),
    STANDARD_BLOCK_TIME("standard_block_time"),
    UTILITY_TOKEN_LATEST("utility_token_latest"),
    FEE_BUG_ONE_ELEVEN_START_BLOCK("fee_bug_one_eleven_start_block"),
    AUTHZ_PROCESSING("authz_processing")
}

class CacheUpdateRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CacheUpdateRecord>(CacheUpdateTable) {
        fun fetchCacheByKey(key: String) = transaction {
            CacheUpdateRecord.find { CacheUpdateTable.cacheKey eq key }.firstOrNull()
        }

        fun updateCacheByKey(key: String, value: String) = transaction {
            fetchCacheByKey(key)?.apply {
                this.cacheValue = value
                this.lastUpdated = LocalDateTime.now()
            } ?: throw IllegalArgumentException("CacheUpdateTable: Key $key was not found as a cached value")
        }
    }

    var cacheKey by CacheUpdateTable.cacheKey
    var description by CacheUpdateTable.description
    var cacheValue by CacheUpdateTable.cacheValue
    var lastUpdated by CacheUpdateTable.lastUpdated
}

object ChainAumHourlyTable : IntIdTable(name = "chain_aum_hourly") {
    val datetime = datetime("datetime")
    val amount = decimal("amount", 100, 0)
    val denom = varchar("denom", 256)
}

class ChainAumHourlyRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ChainAumHourlyRecord>(ChainAumHourlyTable) {
        fun getLatestAumRecord() = transaction {
            ChainAumHourlyRecord
                .all()
                .orderBy(Pair(ChainAumHourlyTable.id, SortOrder.DESC))
                .limit(1)
                .firstOrNull()
        }

        fun getAumForPeriod(fromDate: LocalDateTime, toDate: LocalDateTime) = transaction {
            ChainAumHourlyRecord.find {
                (ChainAumHourlyTable.datetime greaterEq fromDate) and
                        (ChainAumHourlyTable.datetime lessEq toDate.plusDays(1))
            }
                .orderBy(Pair(ChainAumHourlyTable.datetime, SortOrder.ASC))
                .toList()
        }

        fun insertIgnore(date: LocalDateTime, amount: BigDecimal, denom: String) = transaction {
            ChainAumHourlyTable.insertIgnore {
                it[this.datetime] = date
                it[this.amount] = amount
                it[this.denom] = denom
            }
        }
    }

    fun toDto() = ChainAum(this.datetime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:SS")), this.denom, this.amount)

    var datetime by ChainAumHourlyTable.datetime
    var amount by ChainAumHourlyTable.amount
    var denom by ChainAumHourlyTable.denom
}

object TokenHistoricalDailyTable : IdTable<LocalDateTime>(name = "token_historical_daily") {
    val timestamp = datetime("historical_timestamp")
    override val id = timestamp.entityId()
    val data = jsonb<TokenHistoricalDailyTable, CmcHistoricalQuote>("data", OBJECT_MAPPER)
    val dataSource = text("source")
}

class TokenHistoricalDailyRecord(id: EntityID<LocalDateTime>) : Entity<LocalDateTime>(id) {
    companion object : EntityClass<LocalDateTime, TokenHistoricalDailyRecord>(TokenHistoricalDailyTable) {

        fun save(date: LocalDateTime, data: CmcHistoricalQuote, source: String) =
            transaction {
                TokenHistoricalDailyTable.insertIgnore {
                    it[this.timestamp] = date
                    it[this.data] = data
                    it[this.dataSource] = source
                }
            }

        fun findForDates(fromDate: LocalDateTime?, toDate: LocalDateTime?) = transaction {
            val query = TokenHistoricalDailyTable.selectAll()
            if (fromDate != null) {
                query.andWhere { TokenHistoricalDailyTable.timestamp greaterEq fromDate }
            }
            if (toDate != null) {
                query.andWhere { TokenHistoricalDailyTable.timestamp lessEq toDate.plusDays(1) }
            }

            query.orderBy(TokenHistoricalDailyTable.timestamp, SortOrder.ASC)
            TokenHistoricalDailyRecord.wrapRows(query).map { it.data }.toList()
        }

        fun lastKnownPriceForDate(date: LocalDateTime): BigDecimal = transaction {
            TokenHistoricalDailyRecord
                .find { TokenHistoricalDailyTable.timestamp lessEq date }
                .orderBy(Pair(TokenHistoricalDailyTable.timestamp, SortOrder.DESC))
                .limit(1)
                .map { it.data.quote[USD_UPPER]?.close ?: BigDecimal.ZERO }
                .singleOrNull() ?: BigDecimal.ZERO
        }

        fun getLatestDateEntry(): TokenHistoricalDailyRecord? = transaction {
            return@transaction TokenHistoricalDailyRecord
                .all()
                .orderBy(Pair(TokenHistoricalDailyTable.timestamp, SortOrder.DESC))
                .limit(1)
                .firstOrNull()
        }
    }

    var timestamp by TokenHistoricalDailyTable.timestamp
    var data by TokenHistoricalDailyTable.data
}

object ProcessQueueTable : IntIdTable(name = "process_queue") {
    val processType = varchar("process_type", 128)
    val processValue = text("process_value")
    val processing = bool("processing").default(false)
}

enum class ProcessQueueType { ACCOUNT }

class ProcessQueueRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ProcessQueueRecord>(ProcessQueueTable) {

        fun findByType(processType: ProcessQueueType) = transaction {
            ProcessQueueRecord.find {
                (ProcessQueueTable.processType eq processType.name) and
                        (ProcessQueueTable.processing eq false)
            }.toList()
        }

        fun reset(processType: ProcessQueueType) = transaction {
            ProcessQueueTable.update({ ProcessQueueTable.processType eq processType.name }) {
                it[this.processing] = false
            }
        }

        fun delete(processType: ProcessQueueType, value: String) = transaction {
            ProcessQueueTable.deleteWhere {
                (ProcessQueueTable.processType eq processType.name) and
                        (processValue eq value)
            }
        }

        fun insertIgnore(processType: ProcessQueueType, processValue: String) =
            transaction {
                ProcessQueueTable.insertIgnore {
                    it[this.processType] = processType.name
                    it[this.processValue] = processValue
                }
            }
    }

    var processType by ProcessQueueTable.processType
    var processValue by ProcessQueueTable.processValue
    var processing by ProcessQueueTable.processing
}

object PulseCacheTable : IntIdTable(name = "pulse_cache") {
    val cacheDate = date("cache_date")
    val updatedTimestamp = datetime("updated_timestamp")
    val data = jsonb<PulseCacheTable, PulseMetric>("data", OBJECT_MAPPER)
    val type: Column<PulseCacheType> = enumerationByName("type", 128, PulseCacheType::class)
    val subtype = text("subtype").nullable()
}

class PulseCacheRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PulseCacheRecord>(
        PulseCacheTable
    ) {

        fun upsert(date: LocalDate, type: PulseCacheType, data: PulseMetric, subtype: String? = null) = transaction {
            findByDateAndType(date, type, subtype)?.apply {
                this.data = data
                this.updatedTimestamp = LocalDateTime.now(ZoneOffset.UTC)
            } ?:
            PulseCacheTable.insertIgnore {
                it[this.cacheDate] = date
                it[this.updatedTimestamp] = LocalDateTime.now(ZoneOffset.UTC)
                it[this.type] = type
                it[this.subtype] = subtype
                it[this.data] = data
            }
        }

        fun findByDateAndType(date: LocalDate, type: PulseCacheType, subtype: String? = null) =
            transaction {
                PulseCacheRecord.find {
                    (PulseCacheTable.cacheDate eq date) and
                            (PulseCacheTable.type eq type) and
                            (if (subtype != null) PulseCacheTable.subtype eq subtype else PulseCacheTable.subtype.isNull())
                }.firstOrNull()
            }

        fun findByDateSpanAndType(startDate: LocalDate, endDate: LocalDate, type: PulseCacheType, subtype: String? = null) =
            transaction {
                PulseCacheRecord.find {
                    (PulseCacheTable.cacheDate greaterEq startDate) and
                            (PulseCacheTable.cacheDate lessEq endDate) and
                            (PulseCacheTable.type eq type) and
                            (if (subtype != null) PulseCacheTable.subtype eq subtype else PulseCacheTable.subtype.isNull())
                }.orderBy(Pair(PulseCacheTable.cacheDate, SortOrder.ASC))
                .toList()
            }
    }

    var type by PulseCacheTable.type
    var data by PulseCacheTable.data
    var updatedTimestamp by PulseCacheTable.updatedTimestamp
    var subtype by PulseCacheTable.subtype
    var cacheDate by PulseCacheTable.cacheDate
}
