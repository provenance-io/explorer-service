package io.provenance.explorer.domain.entities

import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.core.sql.nullsLast
import io.provenance.explorer.domain.entities.TokenDistributionPaginatedResultsRecord.Companion.batchUpsert
import io.provenance.explorer.domain.extensions.map
import io.provenance.explorer.domain.models.explorer.AssetHolder
import io.provenance.explorer.domain.models.explorer.CountStrTotal
import io.provenance.explorer.domain.models.explorer.TokenDistribution
import io.provenance.explorer.domain.models.explorer.TokenDistributionPaginatedResults
import io.provenance.marker.v1.MarkerAccount
import io.provenance.marker.v1.MarkerStatus
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.math.BigDecimal

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
                .batchUpsert(tokenDistributions, listOf(range, data)) { batch, tokenDistribution ->
                    batch[range] = tokenDistribution.range
                    batch[data] = tokenDistribution
                }
        }

        fun getStats() = transaction {
            val data = TokenDistributionAmountsTable.data
            TokenDistributionAmountsTable
                .slice(data)
                .select { data.isNotNull() }
                .map {
                    it[data]
                }
        }

        private class BatchUpsert(
            table: Table,
            private val onUpdate: List<Column<*>>
        ) : BatchInsertStatement(table, false) {

            override fun prepareSQL(transaction: Transaction): String {
                val onUpdateSQL = if (onUpdate.isNotEmpty()) {
                    " ON CONFLICT (range) " +
                        "DO UPDATE " +
                        "SET data = excluded.data"
                } else ""
                return super.prepareSQL(transaction) + onUpdateSQL
            }
        }

        private fun <T : Table, E> T.batchUpsert(
            data: List<E>,
            onUpdateColumns: List<Column<*>>,
            body: T.(BatchUpsert, E) -> Unit
        ) {
            data.takeIf { it.isNotEmpty() }?.let {
                val insert = BatchUpsert(this, onUpdateColumns)
                data.forEach {
                    insert.addBatch()
                    body(insert, it)
                }
                TransactionManager.current().exec(insert)
            }
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

        fun findByLimitOffset(limit: Any, offset: Int) = transaction {
            val query = """
                SELECT data
                FROM token_distribution_paginated_results
                ORDER BY (data ->> 'count')::double precision DESC
                LIMIT $limit OFFSET $offset
            """.trimIndent()

            TransactionManager.current().exec(query) { it ->
                it.map { OBJECT_MAPPER.readValue(it.getString("data"), CountStrTotal::class.java) }
            }
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
                .batchUpsert(paginatedResults, listOf(ownerAddress, data)) { batch, paginatedResult ->
                    batch[ownerAddress] = paginatedResult.ownerAddress
                    batch[data] = paginatedResult.data
                }
        }

        // With some tinkering from https://github.com/JetBrains/Exposed/issues/167
        // and, https://ohadshai.medium.com/first-steps-with-kotlin-exposed-cb361a9bf5ac
        private class BatchUpsert(
            table: Table,
            private val onUpdate: List<Column<*>>
        ) : BatchInsertStatement(table, false) {

            override fun prepareSQL(transaction: Transaction): String {
                val onUpdateSQL = if (onUpdate.isNotEmpty()) {
                    " ON CONFLICT (owner_address) " +
                        "DO UPDATE " +
                        "SET data = excluded.data"
                } else ""
                return super.prepareSQL(transaction) + onUpdateSQL
            }
        }

        private fun <T : Table, E> T.batchUpsert(
            data: List<E>,
            onUpdateColumns: List<Column<*>>,
            body: T.(BatchUpsert, E) -> Unit
        ) {
            data.takeIf { it.isNotEmpty() }?.let {
                val insert = BatchUpsert(this, onUpdateColumns)
                data.forEach {
                    insert.addBatch()
                    body(insert, it)
                }
                TransactionManager.current().exec(insert)
            }
        }
    }

    var ownerAddress by TokenDistributionPaginatedResultsTable.ownerAddress
    var data by TokenDistributionPaginatedResultsTable.data
}
