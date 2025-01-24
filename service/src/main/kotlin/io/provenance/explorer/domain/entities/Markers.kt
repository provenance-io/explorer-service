package io.provenance.explorer.domain.entities

import cosmos.bank.v1beta1.Bank
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.batchUpsert
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.core.sql.nullsLast
import io.provenance.explorer.domain.core.sql.toDbQueryList
import io.provenance.explorer.domain.extensions.execAndMap
import io.provenance.explorer.domain.models.explorer.TokenDistributionPaginatedResults
import io.provenance.explorer.domain.models.explorer.toCoinStrWithPrice
import io.provenance.explorer.model.AssetHolder
import io.provenance.explorer.model.TokenDistribution
import io.provenance.explorer.model.base.CountStrTotal
import io.provenance.marker.v1.MarkerAccount
import io.provenance.marker.v1.MarkerStatus
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDateTime

object MarkerCacheTable : IntIdTable(name = "marker_cache") {
    val markerAddress = varchar("marker_address", 128).nullable()
    val markerType = varchar("marker_type", 128)
    val denom = varchar("denom", 256)
    val status = varchar("status", 128)
    val supply = decimal("supply", 100, 10)
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
            txTimestamp: LocalDateTime?
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
                }.let { Pair(it!!, findById(it)!!) }
                    .also { if (addr != null) ProcessQueueRecord.insertIgnore(ProcessQueueType.ACCOUNT, addr) }
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

    fun toCoinStrWithPrice(price: BigDecimal?) =
        this.supply.toCoinStrWithPrice(price, this.denom)

    var markerAddress by MarkerCacheTable.markerAddress
    var markerType by MarkerCacheTable.markerType
    var denom by MarkerCacheTable.denom
    var status by MarkerCacheTable.status
    var supply by MarkerCacheTable.supply
    var lastTx by MarkerCacheTable.lastTx
    var data by MarkerCacheTable.data
}

object TokenDistributionAmountsTable : IntIdTable(name = "token_distribution_amounts") {
    val range = varchar("range", 8)
    val data = jsonb<TokenDistributionAmountsTable, TokenDistribution>("data", OBJECT_MAPPER).nullable()
}

class TokenDistributionAmountsRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TokenDistributionAmountsRecord>(TokenDistributionAmountsTable) {

        fun batchUpsert(tokenDistributions: List<TokenDistribution>) = transaction {
            val range = TokenDistributionAmountsTable.range
            val data = TokenDistributionAmountsTable.data
            TokenDistributionAmountsTable
                .batchUpsert(tokenDistributions, listOf(range), listOf(data)) { batch, tokenDistribution ->
                    batch[range] = tokenDistribution.range
                    batch[data] = tokenDistribution
                }
        }

        fun getStats() = transaction {
            val data = TokenDistributionAmountsTable.data
            TokenDistributionAmountsTable
                .slice(data)
                .select { data.isNotNull() }
                .filter { it[data] != null }
                .map { it[data]!! }
        }
    }

    var range by TokenDistributionAmountsTable.range
    var data by TokenDistributionAmountsTable.data
}

object TokenDistributionPaginatedResultsTable : IntIdTable(name = "token_distribution_paginated_results") {
    val ownerAddress = varchar("owner_address", 128)
    val data = jsonb<TokenDistributionPaginatedResultsTable, CountStrTotal>("data", OBJECT_MAPPER)
}

class TokenDistributionPaginatedResultsRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TokenDistributionPaginatedResultsRecord>(TokenDistributionPaginatedResultsTable) {

        fun findByLimitOffset(addresses: Set<String>, limit: Any, offset: Int) = transaction {
            val query = """
                SELECT owner_address, data
                FROM token_distribution_paginated_results
                WHERE owner_address IN (${addresses.toDbQueryList()})
                ORDER BY (data ->> 'count')::double precision DESC
                LIMIT $limit OFFSET $offset
            """.trimIndent()

            query.execAndMap {
                TokenDistributionPaginatedResults(
                    it.getString("owner_address"),
                    OBJECT_MAPPER.readValue(it.getString("data"), CountStrTotal::class.java)
                )
            }
        }

        fun findByAddresses(addresses: Set<String>) = transaction {
            TokenDistributionPaginatedResultsRecord
                .find { TokenDistributionPaginatedResultsTable.ownerAddress inList addresses }
                .toList()
        }

        fun savePaginatedResults(assetHolders: List<AssetHolder>) = transaction {
            val paginatedResults = assetHolders.map {
                TokenDistributionPaginatedResults(
                    ownerAddress = it.ownerAddress,
                    data = it.balance
                )
            }
            batchUpsert(paginatedResults)
        }

        private fun batchUpsert(paginatedResults: List<TokenDistributionPaginatedResults>) = transaction {
            val ownerAddress = TokenDistributionPaginatedResultsTable.ownerAddress
            val data = TokenDistributionPaginatedResultsTable.data
            TokenDistributionPaginatedResultsTable
                .batchUpsert(paginatedResults, listOf(ownerAddress), listOf(data)) { batch, paginatedResult ->
                    batch[ownerAddress] = paginatedResult.ownerAddress
                    batch[data] = paginatedResult.data
                }
        }
    }

    var ownerAddress by TokenDistributionPaginatedResultsTable.ownerAddress
    var data by TokenDistributionPaginatedResultsTable.data
}

object AssetPricingTable : IdTable<Int>(name = "asset_pricing") {
    val markerId = integer("marker_id")
    override val id = markerId.entityId()
    val markerAddress = varchar("marker_address", 128)
    val denom = varchar("denom", 256)
    val pricing = decimal("pricing", 100, 50)
    val pricingDenom = varchar("pricing_denom", 256)
    val lastUpdated = datetime("last_updated")
}

class AssetPricingRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AssetPricingRecord>(AssetPricingTable) {

        private fun findUnique(markerDenom: String, pricingDenom: String): AssetPricingRecord? = transaction {
            AssetPricingRecord.find {
                (AssetPricingTable.denom eq markerDenom) and
                    (AssetPricingTable.pricingDenom eq pricingDenom)
            }
                .limit(1)
                .firstOrNull()
        }
        fun upsert(markerId: Int, markerDenom: String, markerAddress: String?, pricingDenom: String, pricingAmount: BigDecimal, timestamp: LocalDateTime) = transaction {
            findUnique(markerDenom, pricingDenom)?.apply {
                this.pricing = pricingAmount
                this.lastUpdated = timestamp
            } ?: AssetPricingTable.insert {
                it[this.markerId] = markerId
                it[this.markerAddress] = markerAddress ?: ""
                it[this.denom] = markerDenom
                it[this.pricing] = pricingAmount
                it[this.pricingDenom] = pricingDenom
                it[this.lastUpdated] = timestamp
            }
        }

        fun findByDenomList(denoms: List<String>) = transaction {
            AssetPricingRecord.find { AssetPricingTable.denom inList denoms }.toList()
        }

        fun findByDenom(denom: String) = transaction {
            AssetPricingRecord.find { AssetPricingTable.denom eq denom }.firstOrNull()
        }
    }

    var markerId by AssetPricingTable.markerId
    var markerAddress by AssetPricingTable.markerAddress
    var denom by AssetPricingTable.denom
    var pricing by AssetPricingTable.pricing
    var pricingDenom by AssetPricingTable.pricingDenom
    var lastUpdated by AssetPricingTable.lastUpdated
}

object MarkerUnitTable : IntIdTable(name = "marker_unit") {
    val markerId = integer("marker_id")
    val marker = varchar("marker", 256)
    val unit = varchar("unit", 256)
    val exponent = integer("exponent")
}

class MarkerUnitRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MarkerUnitRecord>(MarkerUnitTable) {

        fun insert(id: Int, marker: String, unit: Bank.DenomUnit) = transaction {
            MarkerUnitTable.insertIgnore {
                it[this.markerId] = id
                it[this.marker] = marker
                it[this.unit] = unit.denom
                it[this.exponent] = unit.exponent
            }
        }

        fun findByUnit(unit: String) = transaction {
            MarkerUnitRecord.find { MarkerUnitTable.unit eq unit }.firstOrNull()
        }
    }

    var markerId by MarkerUnitTable.markerId
    var marker by MarkerUnitTable.marker
    var unit by MarkerUnitTable.unit
    var exponent by MarkerUnitTable.exponent
}
