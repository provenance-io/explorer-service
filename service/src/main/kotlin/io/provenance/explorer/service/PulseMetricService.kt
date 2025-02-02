package io.provenance.explorer.service

import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN_BASE_MULTIPLIER
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.BlockCacheHourlyTxCountsRecord
import io.provenance.explorer.domain.entities.PulseCacheRecord
import io.provenance.explorer.domain.extensions.roundWhole
import io.provenance.explorer.domain.models.explorer.pulse.PulseCacheType
import io.provenance.explorer.domain.models.explorer.pulse.PulseMetric
import io.provenance.explorer.model.ValidatorState.ACTIVE
import io.provenance.explorer.model.base.USD_UPPER
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Service handler for the Provenance Pulse application
 */
@Service
class PulseMetricService(
    private val tokenService: TokenService,
    private val validatorService: ValidatorService,
    private val pricingService: PricingService
) {
    protected val logger = logger(PulseMetricService::class)

    val base = UTILITY_TOKEN
    val quote = USD_UPPER

    private fun buildAndSaveMetric(
        date: LocalDate, type: PulseCacheType,
        previous: BigDecimal, current: BigDecimal,
        base: String, quote: String?
    ) =
        PulseMetric.build(
            previous = previous,
            current = current,
            base = base,
            quote = quote
        ).also {
            PulseCacheRecord.upsert(date, type, it)
        }

    private fun fetchCache(
        type: PulseCacheType,
        metricFn: () -> PulseMetric
    ): PulseMetric = transaction {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        val todayCache = PulseCacheRecord.findByDateAndType(today, type)

        val bustCache = scheduledTask() // if this is a scheduled task, bust the cache

        if (todayCache == null || bustCache) {
            val metric = metricFn()
            val yesterdayMetric =
                PulseCacheRecord.findByDateAndType(yesterday, type)?.data
                    ?: buildAndSaveMetric(
                        yesterday, type, metric.amount, metric.amount,
                        metric.base, metric.quote
                    )
            buildAndSaveMetric(
                today, type, yesterdayMetric.amount, metric.amount,
                metric.base, metric.quote
            )
        } else todayCache.data
    }

    /**
     * Returns the current hash market cap metric comparing the previous day's market cap
     * to the current day's market cap
     */
    private fun hashMarketCapMetric(): PulseMetric =
        fetchCache(
            type = PulseCacheType.HASH_MARKET_CAP_METRIC
        ) {
            tokenService.getTokenLatest()?.takeIf { it.quote[quote] != null }?.let {
                it.quote[quote]?.market_cap_by_total_supply?.let { marketCap ->
                    PulseMetric.build(
                        amount = marketCap.divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole(),
                        quote = quote
                    )
                }
            } ?: throw ResourceNotFoundException("No quote found for $quote")
        }

    /**
     * Returns the current hash metrics for the given type
     */
    fun hashMetric(type: PulseCacheType, bustCache: Boolean = false) =
        fetchCache(
            type = type
        ) {
            when (type) {
                PulseCacheType.HASH_STAKED_METRIC -> {
                    val staked = validatorService.getStakingValidators(ACTIVE).sumOf { it.tokenCount }
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole()
                    tokenService.getTokenBreakdown().let {
                        PulseMetric.build(
                            amount = staked,
                            quote = null
                        )
                    }
                }

                PulseCacheType.HASH_CIRCULATING_METRIC -> {
                    val tokenSupply = tokenService.totalSupply()
                                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER)
                                        .roundWhole()
                    PulseMetric.build(
                        amount = tokenSupply,
                        quote = null
                    )
                }

                PulseCacheType.HASH_SUPPLY_METRIC -> {
                    val tokenSupply = tokenService.maxSupply()
                                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER)
                                        .roundWhole()
                    PulseMetric.build(
                        amount = tokenSupply,
                        quote = null
                    )
                }
                else -> throw ResourceNotFoundException("Invalid hash metric request for type $type")
            }
        }

    private fun pulseMarketCap(): PulseMetric =
        fetchCache(
            type = PulseCacheType.PULSE_MARKET_CAP_METRIC
        ) {
            pricingService.getTotalAum().let {
                PulseMetric.build(
                    amount = it
                )
            }
        }

    private fun transactionVolume(): PulseMetric =
        fetchCache(
            type = PulseCacheType.PULSE_TRANSACTION_VOLUME_METRIC
        ) {
            BlockCacheHourlyTxCountsRecord.getTotalTxCount().let {
                PulseMetric.build(
                    amount = it.toBigDecimal(),
                    quote = null
                )
            }
        }

    private fun totalParticipants(): PulseMetric =
        fetchCache(
            type = PulseCacheType.PULSE_PARTICIPANTS_METRIC
        ) {
            AccountRecord.countActiveAccounts().let {
                PulseMetric.build(
                    amount = it.toBigDecimal(),
                    quote = null
                )
            }
        }

    /**
     * Returns the pulse metric for the given type - pulse metrics are "global"
     * metrics that are not specific to Hash
     */
    fun pulseMetric(type: PulseCacheType): PulseMetric {
        return when (type) {
            PulseCacheType.HASH_MARKET_CAP_METRIC -> hashMarketCapMetric()
            PulseCacheType.HASH_STAKED_METRIC -> hashMetric(type)
            PulseCacheType.HASH_CIRCULATING_METRIC -> hashMetric(type)
            PulseCacheType.HASH_SUPPLY_METRIC -> hashMetric(type)
            PulseCacheType.PULSE_MARKET_CAP_METRIC -> pulseMarketCap()
            PulseCacheType.PULSE_TRANSACTION_VOLUME_METRIC -> transactionVolume()
            PulseCacheType.PULSE_FEES_AUCTIONS_METRIC -> pulseMarketCap()
            PulseCacheType.PULSE_RECEIVABLES_METRIC -> pulseMarketCap()
            PulseCacheType.PULSE_TRADE_SETTLEMENT_METRIC -> pulseMarketCap()
            PulseCacheType.PULSE_PARTICIPANTS_METRIC -> totalParticipants()
            PulseCacheType.PULSE_MARGIN_LOANS_METRIC -> pulseMarketCap()
            PulseCacheType.PULSE_DEMOCRATIZED_PRIME_POOLS_METRIC -> pulseMarketCap()
        }
    }

    private fun scheduledTask(): Boolean = Thread.currentThread().name.startsWith("scheduling-")

    fun refreshCache() = transaction {
        val threadName = Thread.currentThread().name
        logger.info("Refreshing pulse cache for thread $threadName")
        PulseCacheType.entries.forEach { type ->
            pulseMetric(type)
        }
    }
}
