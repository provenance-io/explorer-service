package io.provenance.explorer.domain.entities

import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.models.explorer.GasStatistics
import io.provenance.explorer.domain.models.explorer.Spotlight
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.jodatime.date
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.math.BigDecimal

object SpotlightCacheTable : IntIdTable(name = "spotlight_cache") {
    val spotlight = jsonb<SpotlightCacheTable, Spotlight>("spotlight", OBJECT_MAPPER)
    val lastHit = datetime("last_hit")
}

class SpotlightCacheRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SpotlightCacheRecord>(SpotlightCacheTable) {
        fun getSpotlight() = transaction {
            SpotlightCacheRecord.all()
                .orderBy(Pair(SpotlightCacheTable.id, SortOrder.DESC))
                .limit(1)
                .first()
                .spotlight
        }

        fun insertIgnore(json: Spotlight) = transaction {
            SpotlightCacheTable.insertIgnore {
                it[this.spotlight] = json
                it[this.lastHit] = DateTime.now()
            }
        }
    }

    var spotlight by SpotlightCacheTable.spotlight
    var lastHit by SpotlightCacheTable.lastHit
}

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
            date: DateTime
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

        fun findByAddress(address: String, fromDate: DateTime?, toDate: DateTime?, count: Int) = transaction {
            val query = ValidatorMarketRateStatsTable.select { ValidatorMarketRateStatsTable.operatorAddress eq address }
            if (fromDate != null)
                query.andWhere { ValidatorMarketRateStatsTable.date greaterEq fromDate }
            if (toDate != null)
                query.andWhere { ValidatorMarketRateStatsTable.date lessEq toDate.plusDays(1) }

            query.orderBy(ValidatorMarketRateStatsTable.date, SortOrder.DESC).limit(count)
            ValidatorMarketRateStatsRecord.wrapRows(query)
        }
    }

    var operatorAddress by ValidatorMarketRateStatsTable.operatorAddress
    var minMarketRate by ValidatorMarketRateStatsTable.minMarketRate
    var maxMarketRate by ValidatorMarketRateStatsTable.maxMarketRate
    var avgMarketRate by ValidatorMarketRateStatsTable.avgMarketRate
    var date by ValidatorMarketRateStatsTable.date
}

object ChainMarketRateStatsTable : IdTable<DateTime>(name = "chain_market_rate_stats") {
    val date = date("date")
    override val id = date.entityId()
    val minMarketRate = decimal("min_market_rate", 30, 10).nullable()
    val maxMarketRate = decimal("max_market_rate", 30, 10).nullable()
    val avgMarketRate = decimal("avg_market_rate", 30, 10).nullable()
}

class ChainMarketRateStatsRecord(id: EntityID<DateTime>) : Entity<DateTime>(id) {
    companion object : EntityClass<DateTime, ChainMarketRateStatsRecord>(ChainMarketRateStatsTable) {

        fun save(minMarketRate: BigDecimal?, maxMarketRate: BigDecimal?, avgMarketRate: BigDecimal?, date: DateTime) =
            transaction {
                ChainMarketRateStatsTable.insertIgnore {
                    it[this.date] = date
                    it[this.minMarketRate] = minMarketRate
                    it[this.maxMarketRate] = maxMarketRate
                    it[this.avgMarketRate] = avgMarketRate
                }
            }

        fun findForDates(fromDate: DateTime?, toDate: DateTime?, count: Int) = transaction {
            val query = ChainMarketRateStatsTable.selectAll()
            if (fromDate != null)
                query.andWhere { ChainMarketRateStatsTable.date greaterEq fromDate }
            if (toDate != null)
                query.andWhere { ChainMarketRateStatsTable.date lessEq toDate.plusDays(1) }

            query.orderBy(ChainMarketRateStatsTable.date, SortOrder.ASC).limit(count)
            ChainMarketRateStatsRecord.wrapRows(query).map {
                GasStatistics(
                    it.date.toString("yyyy-MM-dd"),
                    it.minMarketRate!!,
                    it.maxMarketRate!!,
                    it.avgMarketRate!!
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
    CHAIN_RELEASES("chain_releases")
}

class CacheUpdateRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CacheUpdateRecord>(CacheUpdateTable) {
        fun fetchCacheByKey(key: String) = transaction {
            CacheUpdateRecord.find { CacheUpdateTable.cacheKey eq key }.firstOrNull()
        }

        fun updateCacheByKey(key: String, value: String) = transaction {
            fetchCacheByKey(key)?.apply {
                this.cacheValue = value
                this.lastUpdated = DateTime.now()
            } ?: throw IllegalArgumentException("CacheUpdateTable: Key $key was not found as a cached value")
        }
    }

    var cacheKey by CacheUpdateTable.cacheKey
    var description by CacheUpdateTable.description
    var cacheValue by CacheUpdateTable.cacheValue
    var lastUpdated by CacheUpdateTable.lastUpdated
}
