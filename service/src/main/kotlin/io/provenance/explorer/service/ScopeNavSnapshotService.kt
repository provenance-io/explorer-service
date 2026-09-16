package io.provenance.explorer.service

import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.ActiveNftScope
import io.provenance.explorer.domain.entities.NftScopeRecord
import io.provenance.explorer.domain.entities.ScopeNavSnapshotRecord
import io.provenance.explorer.domain.entities.ScopeNavSnapshotRow
import io.provenance.explorer.domain.extensions.ChainScopeNav
import io.provenance.explorer.domain.extensions.pickPreferredScopeNav
import io.provenance.explorer.grpc.v1.MetadataGrpcClient
import io.provenance.explorer.model.base.USD_LOWER
import io.provenance.metadata.v1.NetAssetValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Walks indexed scopes and upserts current metadata-module NAVs from chain.
 * This is intentionally not part of the 5-minute Pulse cache refresh.
 */
@Service
class ScopeNavSnapshotService(
    private val metadataGrpcClient: MetadataGrpcClient
) {
    protected val logger = logger(ScopeNavSnapshotService::class)
    private val running = AtomicBoolean(false)

    fun snapshotCurrentNavs() {
        if (!running.compareAndSet(false, true)) {
            logger.warn("Scope NAV snapshot already running, skipping")
            return
        }

        try {
            val started = System.currentTimeMillis()
            val snapshotAt = LocalDateTime.now(ZoneOffset.UTC)
            logger.info("Starting on-chain scope NAV snapshot")

            var afterId = 0
            var processed = 0
            var errors = 0
            var empty = 0

            runBlocking {
                while (true) {
                    val page = NftScopeRecord.findActiveScopesAfter(afterId, PAGE_SIZE)
                    if (page.isEmpty()) break
                    afterId = page.last().id

                    val batchStarted = System.currentTimeMillis()
                    val rows = snapshotPage(page, snapshotAt, height = null)
                    errors += rows.count { it.queryError != null }
                    empty += rows.count {
                        it.queryError == null && (it.priceAmount == null || it.priceAmount == 0L)
                    }
                    ScopeNavSnapshotRecord.batchUpsert(rows)

                    processed += page.size
                    logger.info(
                        "Scope NAV snapshot batch: size=${page.size} errors=${rows.count { it.queryError != null }} " +
                            "elapsedMs=${System.currentTimeMillis() - batchStarted} " +
                            "processed=$processed totalElapsedMs=${System.currentTimeMillis() - started}"
                    )
                }
            }

            ScopeNavSnapshotRecord.pruneDeletedScopes()
            logger.info(
                "Scope NAV snapshot finished: processed=$processed errors=$errors empty=$empty " +
                    "elapsedMs=${System.currentTimeMillis() - started}"
            )
        } catch (e: Exception) {
            logger.error("Scope NAV snapshot failed", e)
        } finally {
            running.set(false)
        }
    }

    /**
     * Preferred USD millidollar NAVs at [height]. Does not write scope_nav_snapshot.
     * Used to persist a historical Pulse cache total for a given date.
     */
    fun usdNavAmountsAtHeight(height: Int): List<Pair<String, BigDecimal>> {
        if (!running.compareAndSet(false, true)) {
            throw IllegalStateException(
                "Scope NAV snapshot already running; cannot query historical NAVs at height $height"
            )
        }

        try {
            val started = System.currentTimeMillis()
            val snapshotAt = LocalDateTime.now(ZoneOffset.UTC)
            logger.info("Starting on-chain scope NAV snapshot at height $height")

            val amounts = mutableListOf<Pair<String, BigDecimal>>()
            var afterId = 0
            var processed = 0
            var errors = 0

            runBlocking {
                while (true) {
                    val page = NftScopeRecord.findActiveScopesAfter(afterId, PAGE_SIZE)
                    if (page.isEmpty()) break
                    afterId = page.last().id

                    val batchStarted = System.currentTimeMillis()
                    val rows = snapshotPage(page, snapshotAt, height)
                    val batchErrors = rows.count { it.queryError != null }
                    errors += batchErrors
                    rows.forEach { row ->
                        val amount = row.priceAmount
                        if (row.queryError == null &&
                            amount != null &&
                            amount > 0L &&
                            row.priceDenom?.equals(USD_LOWER, ignoreCase = true) == true
                        ) {
                            amounts += Pair(row.scopeAddress, BigDecimal(amount))
                        }
                    }

                    processed += page.size
                    logger.info(
                        "Scope NAV snapshot batch: height=$height size=${page.size} errors=$batchErrors " +
                            "elapsedMs=${System.currentTimeMillis() - batchStarted} " +
                            "processed=$processed totalElapsedMs=${System.currentTimeMillis() - started}"
                    )
                }
            }

            logger.info(
                "Scope NAV snapshot finished at height $height: processed=$processed errors=$errors " +
                    "withUsdNav=${amounts.size} elapsedMs=${System.currentTimeMillis() - started}"
            )
            return amounts
        } catch (e: Exception) {
            logger.error("Scope NAV snapshot failed at height $height", e)
            throw e
        } finally {
            running.set(false)
        }
    }

    private suspend fun snapshotPage(
        page: List<ActiveNftScope>,
        snapshotAt: LocalDateTime,
        height: Int?
    ): List<ScopeNavSnapshotRow> = coroutineScope {
        page.map { scope ->
            async { snapshotOne(scope, snapshotAt, height) }
        }.awaitAll()
    }

    private suspend fun snapshotOne(
        scope: ActiveNftScope,
        snapshotAt: LocalDateTime,
        height: Int?
    ): ScopeNavSnapshotRow {
        return try {
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
    }

    companion object {
        private const val PAGE_SIZE = 200
    }
}

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
