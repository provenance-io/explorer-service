package io.provenance.explorer.service

import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN_BASE_MULTIPLIER
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.NavEventsRecord
import io.provenance.explorer.domain.entities.PulseCacheRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.extensions.roundWhole
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.models.explorer.pulse.MetricSeries
import io.provenance.explorer.domain.models.explorer.pulse.PulseCacheType
import io.provenance.explorer.domain.models.explorer.pulse.PulseMetric
import io.provenance.explorer.model.ValidatorState.ACTIVE
import io.provenance.explorer.model.base.USD_LOWER
import io.provenance.explorer.model.base.USD_UPPER
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Service handler for the Provenance Pulse application
 */
@Service
class PulseMetricService(
    private val tokenService: TokenService,
    private val validatorService: ValidatorService,
    private val pricingService: PricingService,
) {
    protected val logger = logger(PulseMetricService::class)

    val base = UTILITY_TOKEN
    val quote = USD_UPPER

    private fun buildAndSavePulseMetric(
        date: LocalDate, type: PulseCacheType,
        previous: BigDecimal, current: BigDecimal,
        base: String,
        quote: String? = null,
        quoteAmount: BigDecimal? = null,
        series: MetricSeries? = null
    ) =
        PulseMetric.build(
            previous = previous,
            current = current,
            base = base,
            quote = quote,
            quoteAmount = quoteAmount,
            series = series
        ).also {
            PulseCacheRecord.upsert(date, type, it)
        }

    private fun fetchOrBuildCacheFromDataSource(
        type: PulseCacheType,
        dataSourceFn: () -> PulseMetric
    ): PulseMetric = transaction {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        val todayCache = PulseCacheRecord.findByDateAndType(today, type)

        // if this is a scheduled task, bust the cache
        val bustCache = scheduledTask()

        if (todayCache == null || bustCache) {
            val metric = dataSourceFn()
            val yesterdayMetric =
                PulseCacheRecord.findByDateAndType(yesterday, type)?.data
                    ?: buildAndSavePulseMetric(
                        date = yesterday,
                        type = type,
                        previous = metric.amount,
                        current = metric.amount,
                        base = metric.base,
                        quote = metric.quote,
                        quoteAmount = metric.quoteAmount,
                        series = metric.series
                    )

            buildAndSavePulseMetric(
                date = today,
                type = type,
                previous = yesterdayMetric.amount,
                current = metric.amount,
                base = metric.base,
                quote = metric.quote,
                quoteAmount = metric.quoteAmount,
                series = metric.series
            )
        } else todayCache.data
    }

    /**
     * Returns the current hash market cap metric comparing the previous day's market cap
     * to the current day's market cap
     */
    private fun hashMarketCapMetric(): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.HASH_MARKET_CAP_METRIC
        ) {
            tokenService.getTokenLatest()?.takeIf { it.quote[quote] != null }?.let {
                    it.quote[quote]?.market_cap_by_total_supply?.let { marketCap ->
                        PulseMetric.build(
                            base = USD_UPPER,
                            amount = marketCap.roundWhole(),
                        )
                    }
                }
                ?: throw ResourceNotFoundException("No quote found for $quote")
        }

    /**
     * Returns the current hash metrics for the given type
     */
    fun hashMetric(type: PulseCacheType, bustCache: Boolean = false) =
        fetchOrBuildCacheFromDataSource(
            type = type
        ) {
            val latestHashPrice =
                tokenService.getTokenLatest()?.quote?.get(USD_UPPER)?.price
                    ?: BigDecimal.ZERO

            when (type) {
                PulseCacheType.HASH_STAKED_METRIC -> {
                    val staked = validatorService.getStakingValidators(ACTIVE)
                        .sumOf { it.tokenCount }
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole()
                    PulseMetric.build(
                        base = UTILITY_TOKEN,
                        amount = staked,
                        quote = USD_UPPER,
                        quoteAmount = latestHashPrice.times(staked)
                    )
                }

                PulseCacheType.HASH_CIRCULATING_METRIC -> {
                    val tokenSupply = tokenService.totalSupply()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER)
                        .roundWhole()
                    PulseMetric.build(
                        base = UTILITY_TOKEN,
                        amount = tokenSupply,
                        quote = USD_UPPER,
                        quoteAmount = latestHashPrice.times(tokenSupply)
                    )
                }

                PulseCacheType.HASH_SUPPLY_METRIC -> {
                    val tokenSupply = tokenService.maxSupply()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER)
                        .roundWhole()
                    PulseMetric.build(
                        base = UTILITY_TOKEN,
                        amount = tokenSupply
                    )
                }

                else -> throw ResourceNotFoundException("Invalid hash metric request for type $type")
            }
        }

    private fun pulseMarketCap(): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_MARKET_CAP_METRIC
        ) {
            pricingService.getTotalAum().let {
                PulseMetric.build(
                    base = USD_UPPER,
                    amount = it
                )
            }
        }

    private fun pulseTradesSettled(): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_TRADE_SETTLEMENT_METRIC
        ) {
            NavEventsRecord.getNavEvents(
                fromDate = LocalDateTime.now().startOfDay()
            ).count { it.source.startsWith("x/exchange") } // gross
             .toBigDecimal().let {
                    PulseMetric.build(
                        base = UTILITY_TOKEN,
                        amount = it
                    )
            }
        }

    private fun pulseTradeValueSettled(): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_TRADE_VALUE_SETTLED_METRIC
        ) {
            NavEventsRecord.getNavEvents(
                fromDate = LocalDateTime.now().startOfDay()
            ).filter {
                it.source.startsWith("x/exchange") &&
                       it.priceDenom?.startsWith("u$USD_LOWER") == true
            } // gross
                .sumOf { it.priceAmount!! }.toBigDecimal().let {
                    PulseMetric.build(
                        base = USD_UPPER,
                        amount = it.divide(1000000.toBigDecimal()) // all uusd is micro
                    )
                }
        }

    private fun pulseReceivableValue(): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_RECEIVABLES_METRIC
        ) { // TODO technically correct assuming only metadata nav events are receivables
            NavEventsRecord.getNavEvents(
                fromDate = LocalDateTime.now().startOfDay()
            ).filter { it.source == "metadata" && it.scopeId != null } // gross
                .sumOf { it.priceAmount!! }.toBigDecimal().let {
                    PulseMetric.build(
                        base = USD_UPPER,
                        amount = it
                    )
                }
        }

    private fun transactionVolume(): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_TRANSACTION_VOLUME_METRIC
        ) {
            val countForDates = TxCacheRecord.countForDates(30)
            val series = MetricSeries(
                seriesData = countForDates.map { it.second.toBigDecimal() },
                labels = countForDates.map {
                    it.first.format(
                        DateTimeFormatter.ofPattern(
                            "MM-dd-yyyy"
                        )
                    )
                }
            )
            PulseMetric.build(
                base = base,
                amount = TxCacheRecord.getTotalTxCount().toBigDecimal(),
                quote = null,
                series = series
            )
        }

    private fun totalParticipants(): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_PARTICIPANTS_METRIC
        ) {
            AccountRecord.countActiveAccounts().let {
                PulseMetric.build(
                    base = UTILITY_TOKEN,
                    amount = it.toBigDecimal(),
                )
            }
        }

    private fun todoPulse(): PulseMetric =
        PulseMetric.build(
            base = UTILITY_TOKEN,
            amount = BigDecimal.ZERO
        )

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
            PulseCacheType.PULSE_RECEIVABLES_METRIC -> pulseReceivableValue()
            PulseCacheType.PULSE_TRADE_SETTLEMENT_METRIC -> pulseTradesSettled()
            PulseCacheType.PULSE_TRADE_VALUE_SETTLED_METRIC -> pulseTradeValueSettled()
            PulseCacheType.PULSE_PARTICIPANTS_METRIC -> totalParticipants()
            PulseCacheType.PULSE_DEMOCRATIZED_PRIME_POOLS_METRIC -> todoPulse()
            PulseCacheType.PULSE_MARGIN_LOANS_METRIC -> todoPulse()
            PulseCacheType.PULSE_FEES_AUCTIONS_METRIC -> todoPulse()
        }
    }

    private fun scheduledTask(): Boolean =
        Thread.currentThread().name.startsWith("scheduling-")

    fun refreshCache() = transaction {
        val threadName = Thread.currentThread().name
        logger.info("Refreshing pulse cache for thread $threadName")
        PulseCacheType.entries.forEach { type ->
            pulseMetric(type)
        }
        logger.info("Pulse cache refreshed for thread $threadName")
    }
}
