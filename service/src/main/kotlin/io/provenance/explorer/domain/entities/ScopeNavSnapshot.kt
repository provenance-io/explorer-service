package io.provenance.explorer.domain.entities

import io.provenance.explorer.domain.core.sql.batchUpsert
import io.provenance.explorer.domain.extensions.execAndMap
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDateTime

data class ScopeNavSnapshotRow(
    val scopeUuid: String,
    val scopeAddress: String,
    val priceAmount: Long?,
    val priceDenom: String?,
    val volume: Long?,
    val updatedBlockHeight: Long?,
    val snapshotAt: LocalDateTime,
    val queryError: String?
)

object ScopeNavSnapshotTable : IntIdTable(name = "scope_nav_snapshot") {
    val scopeUuid = varchar("scope_uuid", 128)
    val scopeAddress = varchar("scope_address", 128)
    val priceAmount = long("price_amount").nullable()
    val priceDenom = varchar("price_denom", 128).nullable()
    val volume = long("volume").nullable()
    val updatedBlockHeight = long("updated_block_height").nullable()
    val snapshotAt = datetime("snapshot_at")
    val queryError = text("query_error").nullable()
}

class ScopeNavSnapshotRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ScopeNavSnapshotRecord>(ScopeNavSnapshotTable) {

        fun batchUpsert(rows: List<ScopeNavSnapshotRow>) = transaction {
            if (rows.isEmpty()) return@transaction
            ScopeNavSnapshotTable.batchUpsert(
                rows,
                listOf(ScopeNavSnapshotTable.scopeAddress),
                listOf(
                    ScopeNavSnapshotTable.scopeUuid,
                    ScopeNavSnapshotTable.priceAmount,
                    ScopeNavSnapshotTable.priceDenom,
                    ScopeNavSnapshotTable.volume,
                    ScopeNavSnapshotTable.updatedBlockHeight,
                    ScopeNavSnapshotTable.snapshotAt,
                    ScopeNavSnapshotTable.queryError
                )
            ) { batch, row ->
                batch[scopeUuid] = row.scopeUuid
                batch[scopeAddress] = row.scopeAddress
                batch[priceAmount] = row.priceAmount
                batch[priceDenom] = row.priceDenom
                batch[volume] = row.volume
                batch[updatedBlockHeight] = row.updatedBlockHeight
                batch[snapshotAt] = row.snapshotAt
                batch[queryError] = row.queryError
            }
        }

        /**
         * Latest USD millidollar NAVs from the on-chain snapshot. Ignores query
         * failures and rows with no USD price.
         */
        fun usdNavAmounts(): List<Pair<String, BigDecimal>> = transaction {
            """
                SELECT scope_address, price_amount
                FROM scope_nav_snapshot
                WHERE price_amount > 0
                  AND query_error IS NULL
                  AND price_denom = 'usd'
            """.trimIndent().execAndMap {
                Pair(it.getString("scope_address"), BigDecimal(it.getString("price_amount")))
            }
        }

        fun pruneDeletedScopes() = transaction {
            exec(
                """
                    DELETE FROM scope_nav_snapshot sns
                    WHERE NOT EXISTS (
                        SELECT 1 FROM nft_scope ns
                        WHERE ns.address = sns.scope_address
                          AND ns.deleted = false
                    )
                """.trimIndent()
            )
        }
    }

    var scopeUuid by ScopeNavSnapshotTable.scopeUuid
    var scopeAddress by ScopeNavSnapshotTable.scopeAddress
    var priceAmount by ScopeNavSnapshotTable.priceAmount
    var priceDenom by ScopeNavSnapshotTable.priceDenom
    var volume by ScopeNavSnapshotTable.volume
    var updatedBlockHeight by ScopeNavSnapshotTable.updatedBlockHeight
    var snapshotAt by ScopeNavSnapshotTable.snapshotAt
    var queryError by ScopeNavSnapshotTable.queryError
}
