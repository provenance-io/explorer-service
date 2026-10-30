package io.provenance.explorer.domain.entities

import io.provenance.explorer.domain.core.sql.batchUpsert
import io.provenance.explorer.domain.core.sql.toDbQueryList
import io.provenance.explorer.domain.extensions.execAndMap
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Live snapshot cadence from the last successful
 * `PULSE_SCOPE_NAV_SNAPSHOT_METRIC` write, not `scope_nav_snapshot.snapshot_at`.
 */
fun snapshotWalkDue(
    now: LocalDateTime,
    lastMetricUpdated: LocalDateTime?,
    maxAge: Duration,
    endOfDayFrom: LocalTime = LocalTime.of(22, 0)
): Boolean {
    if (lastMetricUpdated == null) return true
    if (lastMetricUpdated.isBefore(now.minus(maxAge))) return true
    val eveningStart = now.toLocalDate().atTime(endOfDayFrom)
    return !now.isBefore(eveningStart) && lastMetricUpdated.isBefore(eveningStart)
}

fun snapshotWalkDueReason(
    now: LocalDateTime,
    lastMetricUpdated: LocalDateTime?,
    maxAge: Duration,
    endOfDayFrom: LocalTime = LocalTime.of(22, 0)
): String = when {
    lastMetricUpdated == null -> "never"
    lastMetricUpdated.isBefore(now.minus(maxAge)) -> "stale"
    else -> "end-of-day"
}

data class ScopeNavSnapshotRow(
    val scopeUuid: String,
    val scopeAddress: String,
    val priceAmount: Long?,
    val priceDenom: String?,
    val volume: Long?,
    val updatedBlockHeight: Long?,
    val snapshotAt: LocalDateTime,
    val queryError: String?,
    val nextPmtAmt: Long? = null,
    val nextPmtDenom: String? = null,
    val nextPmtDate: LocalDate? = null,
    val maturityDate: LocalDate? = null,
    val loanStatus: String? = null,
    val interestRate: Int? = null
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
    val nextPmtAmt = long("next_pmt_amt").nullable()
    val nextPmtDenom = varchar("next_pmt_denom", 128).nullable()
    val nextPmtDate = date("next_pmt_date").nullable()
    val maturityDate = date("maturity_date").nullable()
    val loanStatus = varchar("loan_status", 50).nullable()
    val interestRate = integer("interest_rate").nullable()
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
                    ScopeNavSnapshotTable.queryError,
                    ScopeNavSnapshotTable.nextPmtAmt,
                    ScopeNavSnapshotTable.nextPmtDenom,
                    ScopeNavSnapshotTable.nextPmtDate,
                    ScopeNavSnapshotTable.maturityDate,
                    ScopeNavSnapshotTable.loanStatus,
                    ScopeNavSnapshotTable.interestRate
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
                batch[nextPmtAmt] = row.nextPmtAmt
                batch[nextPmtDenom] = row.nextPmtDenom
                batch[nextPmtDate] = row.nextPmtDate
                batch[maturityDate] = row.maturityDate
                batch[loanStatus] = row.loanStatus
                batch[interestRate] = row.interestRate
            }
        }

        /**
         * Sum of latest USD millidollar NAVs. Postgres sums so the live metric
         * write does not materialize one row per scope.
         */
        fun usdNavAmountSum(ignoredScopeAddresses: Set<String> = emptySet()): BigDecimal =
            transaction {
                val ignoredFilter = if (ignoredScopeAddresses.isEmpty()) {
                    ""
                } else {
                    "AND scope_address NOT IN (${ignoredScopeAddresses.toDbQueryList()})"
                }
                """
                    SELECT COALESCE(SUM(price_amount), 0) AS total
                    FROM scope_nav_snapshot
                    WHERE price_amount > 0
                      AND query_error IS NULL
                      AND price_denom = 'usd'
                      $ignoredFilter
                """.trimIndent().execAndMap {
                    BigDecimal(it.getString("total"))
                }.first()
            }

        /**
         * Latest USD NAVs for scopes whose specification is in [specificationIds].
         * [ScopeSpecNavAmount.priceAmount] is the raw stored amount; callers
         * scale with the ledger entity's usd_pricing_exponent.
         * Empty [specificationIds] returns no rows.
         */
        fun usdNavAmountsForSpecs(specificationIds: Set<String>): List<ScopeSpecNavAmount> =
            transaction {
                if (specificationIds.isEmpty()) return@transaction emptyList()
                """
                    SELECT DISTINCT ON (sns.scope_address)
                           sns.scope_address,
                           sns.price_amount,
                           ns.scope ->> 'specification_id' AS specification_id
                    FROM scope_nav_snapshot sns
                    JOIN nft_scope ns
                      ON ns.address = sns.scope_address
                     AND ns.deleted = false
                    WHERE sns.price_amount > 0
                      AND sns.query_error IS NULL
                      AND sns.price_denom = 'usd'
                      AND ns.scope ->> 'specification_id' IN (${specificationIds.toDbQueryList()})
                    ORDER BY sns.scope_address, ns.id
                """.trimIndent().execAndMap {
                    ScopeSpecNavAmount(
                        scopeAddress = it.getString("scope_address"),
                        specificationId = it.getString("specification_id") ?: "",
                        priceAmount = BigDecimal(it.getString("price_amount"))
                    )
                }.filter { it.specificationId.isNotBlank() }
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
    var nextPmtAmt by ScopeNavSnapshotTable.nextPmtAmt
    var nextPmtDenom by ScopeNavSnapshotTable.nextPmtDenom
    var nextPmtDate by ScopeNavSnapshotTable.nextPmtDate
    var maturityDate by ScopeNavSnapshotTable.maturityDate
    var loanStatus by ScopeNavSnapshotTable.loanStatus
    var interestRate by ScopeNavSnapshotTable.interestRate
}

data class ScopeSpecNavAmount(
    val scopeAddress: String,
    val specificationId: String,
    val priceAmount: BigDecimal
)

/** Per-spec totals from a height walk. Avoids retaining one row per scope. */
data class SpecNavAggregate(
    val specificationId: String,
    val priceAmountSum: BigDecimal,
    val scopeCount: Int
)
