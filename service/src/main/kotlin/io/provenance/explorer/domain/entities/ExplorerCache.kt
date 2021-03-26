package io.provenance.explorer.domain.entities

import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
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


object SpotlightCacheTable : IdTable<Int>(name = "spotlight_cache") {
    override val id = integer("id").entityId()
    val spotlight = jsonb<SpotlightCacheTable, Spotlight>("spotlight", OBJECT_MAPPER)
    val lastHit = datetime("last_hit")
}

class SpotlightCacheRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SpotlightCacheRecord>(SpotlightCacheTable) {
        fun getIndex() = transaction {
            SpotlightCacheRecord.findById(1)
        }

        fun insertIgnore(json: Spotlight) = transaction {
            (getIndex() ?: new(1) {}).apply {
                this.spotlight = json
                this.lastHit = DateTime.now()
            }
        }
    }

    var spotlight by SpotlightCacheTable.spotlight
    var lastHit by SpotlightCacheTable.lastHit
}

object ValidatorGasFeeCacheTable : IntIdTable(name = "validator_gas_fee_cache") {
    val date = date("date")
    val operatorAddress = varchar("operator_address", 96)
    val minGasFee = decimal("min_gas_fee", 30, 10).nullable()
    val maxGasFee = decimal("max_gas_fee", 30, 10).nullable()
    val avgGasFee = decimal("avg_gas_fee", 30, 10).nullable()

    init {
        index(true, date, operatorAddress)
    }
}

class ValidatorGasFeeCacheRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ValidatorGasFeeCacheRecord>(ValidatorGasFeeCacheTable) {

        fun save(
            address: String,
            minGasFee: BigDecimal?,
            maxGasFee: BigDecimal?,
            avgGasFee: BigDecimal?,
            date: DateTime
        ) =
            transaction {
                ValidatorGasFeeCacheTable.insertIgnore {
                    it[this.operatorAddress] = address
                    it[this.minGasFee] = minGasFee
                    it[this.maxGasFee] = maxGasFee
                    it[this.avgGasFee] = avgGasFee
                    it[this.date] = date
                }
            }

        fun findByAddress(address: String, fromDate: DateTime?, toDate: DateTime?, count: Int) = transaction {
            val query = ValidatorGasFeeCacheTable.select { ValidatorGasFeeCacheTable.operatorAddress eq address }
            if (fromDate != null)
                query.andWhere { ValidatorGasFeeCacheTable.date greaterEq fromDate }
            if (toDate != null)
                query.andWhere { ValidatorGasFeeCacheTable.date lessEq toDate.plusDays(1) }

            query.orderBy(ValidatorGasFeeCacheTable.date, SortOrder.DESC).limit(count)
            ValidatorGasFeeCacheRecord.wrapRows(query)
        }
    }

    var operatorAddress by ValidatorGasFeeCacheTable.operatorAddress
    var minGasFee by ValidatorGasFeeCacheTable.minGasFee
    var maxGasFee by ValidatorGasFeeCacheTable.maxGasFee
    var avgGasFee by ValidatorGasFeeCacheTable.avgGasFee
    var date by ValidatorGasFeeCacheTable.date
}

object ChainGasFeeCacheTable : IdTable<DateTime>(name = "chain_gas_fee_cache") {
    val date = date("date")
    override val id = date.entityId()
    val minGasFee = decimal("min_gas_fee", 30, 10).nullable()
    val maxGasFee = decimal("max_gas_fee", 30, 10).nullable()
    val avgGasFee = decimal("avg_gas_fee", 30, 10).nullable()
}

class ChainGasFeeCacheRecord(id: EntityID<DateTime>) : Entity<DateTime>(id) {
    companion object : EntityClass<DateTime, ChainGasFeeCacheRecord>(ChainGasFeeCacheTable) {

        fun save(minGasFee: BigDecimal?, maxGasFee: BigDecimal?, avgGasFee: BigDecimal?, date: DateTime) =
            transaction {
                ChainGasFeeCacheTable.insertIgnore {
                    it[this.date] = date
                    it[this.minGasFee] = minGasFee
                    it[this.maxGasFee] = maxGasFee
                    it[this.avgGasFee] = avgGasFee
                }
            }

        fun findForDates(fromDate: DateTime?, toDate: DateTime?, count: Int) = transaction {
            val query = ChainGasFeeCacheTable.selectAll()
            if (fromDate != null)
                query.andWhere { ChainGasFeeCacheTable.date greaterEq fromDate }
            if (toDate != null)
                query.andWhere { ChainGasFeeCacheTable.date lessEq toDate.plusDays(1) }

            query.orderBy(ChainGasFeeCacheTable.date, SortOrder.DESC).limit(count)
            ChainGasFeeCacheRecord.wrapRows(query)
        }
    }

    var date by ChainGasFeeCacheTable.date
    var minGasFee by ChainGasFeeCacheTable.minGasFee
    var maxGasFee by ChainGasFeeCacheTable.maxGasFee
    var avgGasFee by ChainGasFeeCacheTable.avgGasFee
}
