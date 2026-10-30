package io.provenance.explorer.service

import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.ActiveNftScope
import io.provenance.explorer.domain.entities.LedgerEntitySpecRecord
import io.provenance.explorer.domain.entities.NftScopeRecord
import io.provenance.explorer.domain.entities.PulseCacheRecord
import io.provenance.explorer.domain.entities.ScopeNavSnapshotRecord
import io.provenance.explorer.domain.entities.ScopeNavSnapshotRow
import io.provenance.explorer.domain.entities.SpecNavAggregate
import io.provenance.explorer.domain.entities.snapshotWalkDue
import io.provenance.explorer.domain.entities.snapshotWalkDueReason
import io.provenance.explorer.domain.extensions.ChainScopeNav
import io.provenance.explorer.domain.extensions.ledgerDaysSinceEpochToDate
import io.provenance.explorer.domain.extensions.pickPreferredScopeNav
import io.provenance.explorer.domain.extensions.specificationIdToScopeSpecAddr
import io.provenance.explorer.domain.models.explorer.pulse.EntityType
import io.provenance.explorer.domain.models.explorer.pulse.PulseCacheType
import io.provenance.explorer.grpc.v1.ChainLedger
import io.provenance.explorer.grpc.v1.LedgerGrpcClient
import io.provenance.explorer.grpc.v1.MetadataGrpcClient
import io.provenance.explorer.model.base.USD_LOWER
import io.provenance.metadata.v1.NetAssetValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Walks indexed scopes and upserts current metadata-module NAVs from chain.
 * This is intentionally not part of the 5-minute Pulse cache refresh.
 */
@Service
class ScopeNavSnapshotService(
    private val metadataGrpcClient: MetadataGrpcClient,
    private val ledgerGrpcClient: LedgerGrpcClient
) {
    protected val logger = logger(ScopeNavSnapshotService::class)
    private val running = AtomicBoolean(false)

    fun isWalkRunning(): Boolean = running.get()

    /**
     * Runs a live snapshot when today's `PULSE_SCOPE_NAV_SNAPSHOT_METRIC` row
     * is missing, older than [maxAge], or it is after 22:00 UTC and that row
     * was written earlier today. Historical backfill does not use this path.
     */
    fun snapshotCurrentNavsIfDue(maxAge: Duration = SNAPSHOT_MAX_AGE): Boolean {
        if (running.get()) {
            logger.info("Scope NAV snapshot already running, skipping")
            return false
        }

        val now = LocalDateTime.now(ZoneOffset.UTC)
        val lastMetricUpdated = PulseCacheRecord.findByDateAndType(
            now.toLocalDate(),
            PulseCacheType.PULSE_SCOPE_NAV_SNAPSHOT_METRIC
        )?.updatedTimestamp
        if (!snapshotWalkDue(now, lastMetricUpdated, maxAge, END_OF_DAY_FROM)) {
            val ageMs = lastMetricUpdated?.let { Duration.between(it, now).toMillis() }
            logger.info(
                "Scope NAV snapshot skipped: metricUpdated=$lastMetricUpdated " +
                    "ageMs=$ageMs maxAgeHours=${maxAge.toHours()}"
            )
            return false
        }

        val reason = snapshotWalkDueReason(now, lastMetricUpdated, maxAge, END_OF_DAY_FROM)
        logger.info("Scope NAV snapshot due ($reason): metricUpdated=$lastMetricUpdated")
        return snapshotCurrentNavs()
    }

    fun snapshotCurrentNavs(): Boolean {
        if (!running.compareAndSet(false, true)) {
            logger.warn("Scope NAV snapshot already running, skipping")
            return false
        }

        try {
            val started = System.currentTimeMillis()
            val snapshotAt = LocalDateTime.now(ZoneOffset.UTC)
            val loanSpecIds = LedgerEntitySpecRecord.findSpecificationIdsByEntityType(EntityType.LOANS)
            logger.info(
                "Starting on-chain scope NAV snapshot loanSpecs=${loanSpecIds.size}"
            )

            var afterAddress = ""
            var processed = 0
            var errors = 0
            var empty = 0

            runBlocking {
                while (true) {
                    val page = NftScopeRecord.findActiveScopesAfter(afterAddress, PAGE_SIZE)
                    if (page.isEmpty()) break
                    afterAddress = page.last().address

                    val batchStarted = System.currentTimeMillis()
                    val rows = snapshotPage(
                        page,
                        snapshotAt,
                        height = null,
                        loanSpecIds = loanSpecIds
                    )
                    errors += rows.count { it.queryError != null }
                    empty += rows.count {
                        it.queryError == null && (it.priceAmount == null || it.priceAmount == 0L)
                    }
                    ScopeNavSnapshotRecord.batchUpsert(rows)

                    processed += page.size
                    logger.info(
                        "Scope NAV snapshot batch: size=${page.size} errors=${rows.count { it.queryError != null }} " +
                            "loanHeaders=${rows.count { it.loanStatus != null }} " +
                            "elapsedMs=${System.currentTimeMillis() - batchStarted} " +
                            "processed=$processed totalElapsedMs=${System.currentTimeMillis() - started}"
                    )
                }
            }

            logger.info("Scope NAV snapshot prune starting: processed=$processed")
            ScopeNavSnapshotRecord.pruneDeletedScopes()
            logger.info(
                "Scope NAV snapshot finished: processed=$processed errors=$errors empty=$empty " +
                    "snapshotAt=$snapshotAt elapsedMs=${System.currentTimeMillis() - started}"
            )
            return true
        } catch (e: Exception) {
            logger.error("Scope NAV snapshot failed", e)
            return false
        } finally {
            running.set(false)
        }
    }

    /**
     * Sum of preferred USD millidollar NAVs at [height]. Does not write
     * `scope_nav_snapshot`. Used for a historical Pulse cache total.
     */
    fun usdNavAmountsAtHeight(
        height: Int,
        ignoredScopeAddresses: Set<String> = emptySet()
    ): BigDecimal =
        walkSpecNavsAtHeight(
            height = height,
            label = "all",
            ignoredScopeAddresses = ignoredScopeAddresses
        ) { afterAddress ->
            NftScopeRecord.findActiveScopesAfter(afterAddress, PAGE_SIZE)
        }.sumOf { it.priceAmountSum }

    /**
     * Per-spec USD millidollar totals at [height] for scopes whose
     * specification_id is in [specificationIds]. Does not retain one row
     * per scope.
     */
    fun usdNavAmountsAtHeightForSpecs(
        specificationIds: Set<String>,
        height: Int,
        ignoredScopeAddresses: Set<String> = emptySet()
    ): List<SpecNavAggregate> {
        if (specificationIds.isEmpty()) return emptyList()
        return walkSpecNavsAtHeight(
            height = height,
            label = "spec",
            ignoredScopeAddresses = ignoredScopeAddresses
        ) { afterAddress ->
            NftScopeRecord.findActiveScopesAfter(afterAddress, PAGE_SIZE, specificationIds)
        }.filter { it.specificationId.isNotBlank() }
    }

    private fun walkSpecNavsAtHeight(
        height: Int,
        label: String,
        ignoredScopeAddresses: Set<String>,
        nextPage: (String) -> List<ActiveNftScope>
    ): List<SpecNavAggregate> {
        if (!running.compareAndSet(false, true)) {
            throw IllegalStateException(
                "Scope NAV snapshot already running; cannot query historical NAVs at height $height"
            )
        }

        try {
            val started = System.currentTimeMillis()
            val snapshotAt = LocalDateTime.now(ZoneOffset.UTC)
            logger.info("Starting on-chain $label scope NAV snapshot at height $height")

            val sums = mutableMapOf<String, BigDecimal>()
            val counts = mutableMapOf<String, Int>()
            var afterAddress = ""
            var processed = 0
            var errors = 0
            var withUsdNav = 0

            runBlocking {
                while (true) {
                    val page = nextPage(afterAddress)
                    if (page.isEmpty()) break
                    afterAddress = page.last().address
                    val specByAddress = page.associate { it.address to it.specificationId }

                    val batchStarted = System.currentTimeMillis()
                    val rows = snapshotPage(page, snapshotAt, height)
                    val batchErrors = rows.count { it.queryError != null }
                    errors += batchErrors
                    rows.forEach { row ->
                        val amount = row.priceAmount
                        if (row.queryError == null &&
                            amount != null &&
                            amount > 0L &&
                            row.priceDenom?.equals(USD_LOWER, ignoreCase = true) == true &&
                            row.scopeAddress !in ignoredScopeAddresses
                        ) {
                            val spec = specByAddress[row.scopeAddress].orEmpty()
                            if (spec.isBlank()) return@forEach
                            sums.merge(spec, BigDecimal(amount), BigDecimal::add)
                            counts.merge(spec, 1, Int::plus)
                            withUsdNav++
                        }
                    }

                    processed += page.size
                    logger.info(
                        "Scope NAV snapshot batch: height=$height label=$label size=${page.size} " +
                            "errors=$batchErrors elapsedMs=${System.currentTimeMillis() - batchStarted} " +
                            "processed=$processed totalElapsedMs=${System.currentTimeMillis() - started}"
                    )
                }
            }

            val aggregates = sums.map { (spec, sum) ->
                SpecNavAggregate(
                    specificationId = spec,
                    priceAmountSum = sum,
                    scopeCount = counts[spec] ?: 0
                )
            }
            logger.info(
                "Scope NAV snapshot finished at height $height label=$label: processed=$processed " +
                    "errors=$errors withUsdNav=$withUsdNav specs=${aggregates.size} " +
                    "elapsedMs=${System.currentTimeMillis() - started}"
            )
            return aggregates
        } catch (e: Exception) {
            logger.error("Scope NAV snapshot failed at height $height label=$label", e)
            throw e
        } finally {
            running.set(false)
        }
    }

    private suspend fun snapshotPage(
        page: List<ActiveNftScope>,
        snapshotAt: LocalDateTime,
        height: Int?,
        loanSpecIds: Set<String> = emptySet()
    ): List<ScopeNavSnapshotRow> = coroutineScope {
        page.map { scope ->
            async {
                try {
                    snapshotOne(scope, snapshotAt, height, loanSpecIds)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logger.warn("Scope snapshot failed for ${scope.address}: ${e.message}")
                    ScopeNavSnapshotRow(
                        scopeUuid = scope.uuid,
                        scopeAddress = scope.address,
                        priceAmount = null,
                        priceDenom = null,
                        volume = null,
                        updatedBlockHeight = null,
                        snapshotAt = snapshotAt,
                        queryError = e.message?.take(1000)
                    )
                }
            }
        }.awaitAll()
    }

    private suspend fun snapshotOne(
        scope: ActiveNftScope,
        snapshotAt: LocalDateTime,
        height: Int?,
        loanSpecIds: Set<String>
    ): ScopeNavSnapshotRow {
        val nav = queryScopeNav(scope, snapshotAt, height)
        val loanHeader = queryLoanLedgerHeader(scope, height, loanSpecIds)
        return nav.copy(
            nextPmtAmt = loanHeader.nextPmtAmt,
            nextPmtDenom = loanHeader.nextPmtDenom,
            nextPmtDate = loanHeader.nextPmtDate,
            maturityDate = loanHeader.maturityDate,
            loanStatus = loanHeader.loanStatus,
            interestRate = loanHeader.interestRate
        )
    }

    private suspend fun queryScopeNav(
        scope: ActiveNftScope,
        snapshotAt: LocalDateTime,
        height: Int?
    ): ScopeNavSnapshotRow =
        try {
            val navs = metadataGrpcClient.getScopeNetAssetValues(scope.address, height)
                .netAssetValuesList
                .mapNotNull { it.toChainScopeNav() }
            val preferred = pickPreferredScopeNav(navs)
            ScopeNavSnapshotRow(
                scopeUuid = scope.uuid,
                scopeAddress = scope.address,
                priceAmount = preferred?.priceAmount,
                priceDenom = preferred?.priceDenom,
                volume = preferred?.volume,
                updatedBlockHeight = preferred?.updatedBlockHeight,
                snapshotAt = snapshotAt,
                queryError = null
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.warn("Failed to query scope NAV for ${scope.address}: ${e.message}")
            ScopeNavSnapshotRow(
                scopeUuid = scope.uuid,
                scopeAddress = scope.address,
                priceAmount = null,
                priceDenom = null,
                volume = null,
                updatedBlockHeight = null,
                snapshotAt = snapshotAt,
                queryError = e.message?.take(1000)
            )
        }

    /**
     * Best-effort loan header. Failures leave ledger columns null and
     * never fail the NAV upsert for this scope.
     */
    private suspend fun queryLoanLedgerHeader(
        scope: ActiveNftScope,
        height: Int?,
        loanSpecIds: Set<String>
    ): LoanLedgerHeader {
        if (height != null || scope.specificationId !in loanSpecIds) {
            return LoanLedgerHeader()
        }
        return try {
            loanLedgerHeader(scope)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.warn("Failed to query loan ledger for ${scope.address}: ${e.message}")
            LoanLedgerHeader()
        }
    }

    private suspend fun loanLedgerHeader(scope: ActiveNftScope): LoanLedgerHeader {
        val specAddr = specificationIdToScopeSpecAddr(scope.specificationId.orEmpty())
            ?: return LoanLedgerHeader()
        val ledger = ledgerGrpcClient.getLedger(specAddr, scope.address)
            ?: return LoanLedgerHeader()
        return headerFromLedger(ledger)
    }

    private suspend fun headerFromLedger(ledger: ChainLedger): LoanLedgerHeader {
        val nextPmtAmt = ledger.nextPmtAmt.toLongOrNull()
        val nextPmtDate = ledgerDaysSinceEpochToDate(ledger.nextPmtDate)
        val nextPmtDenom = if (nextPmtAmt != null && nextPmtAmt > 0) {
            ignoreLedgerLookupFailure { ledgerGrpcClient.classDenom(ledger.ledgerClassId) }
        } else {
            null
        }
        val loanStatus = ignoreLedgerLookupFailure {
            ledgerGrpcClient.statusCode(ledger.ledgerClassId, ledger.statusTypeId)
        }
        return LoanLedgerHeader(
            nextPmtAmt = if (nextPmtAmt != null && nextPmtAmt > 0) nextPmtAmt else null,
            nextPmtDenom = nextPmtDenom,
            nextPmtDate = nextPmtDate,
            maturityDate = ledgerDaysSinceEpochToDate(ledger.maturityDate),
            loanStatus = loanStatus,
            interestRate = ledger.interestRate.takeIf { it > 0 }
        )
    }

    private suspend fun <T> ignoreLedgerLookupFailure(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    companion object {
        private const val PAGE_SIZE = 200
        val SNAPSHOT_MAX_AGE: Duration = Duration.ofHours(6)
        val END_OF_DAY_FROM: LocalTime = LocalTime.of(22, 0)
    }
}

private data class LoanLedgerHeader(
    val nextPmtAmt: Long? = null,
    val nextPmtDenom: String? = null,
    val nextPmtDate: LocalDate? = null,
    val maturityDate: LocalDate? = null,
    val loanStatus: String? = null,
    val interestRate: Int? = null
)

private fun NetAssetValue.toChainScopeNav(): ChainScopeNav? {
    if (!hasPrice()) return null
    val amount = price.amount.toLongOrNull() ?: return null
    if (price.denom.isBlank()) return null
    return ChainScopeNav(
        priceAmount = amount,
        priceDenom = price.denom,
        volume = volume,
        updatedBlockHeight = updatedBlockHeight
    )
}
