package io.provenance.explorer.service

import cosmos.bank.v1beta1.Bank
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
import io.provenance.explorer.domain.models.explorer.pulse.PulseAssetSummary
import io.provenance.explorer.domain.models.explorer.pulse.PulseCacheType
import io.provenance.explorer.domain.models.explorer.pulse.PulseMetric
import io.provenance.explorer.model.ValidatorState.ACTIVE
import io.provenance.explorer.model.base.USD_LOWER
import io.provenance.explorer.model.base.USD_UPPER
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.pow

/**
 * Service handler for the Provenance Pulse application
 */
@Service
class PulseMetricService(
    private val tokenService: TokenService,
    private val validatorService: ValidatorService,
    private val pricingService: PricingService,
    private val assetService: AssetService
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
        series: MetricSeries? = null,
        subtype: String? = null
    ) =
        PulseMetric.build(
            previous = previous,
            current = current,
            base = base,
            quote = quote,
            quoteAmount = quoteAmount,
            series = series
        ).also {
            PulseCacheRecord.upsert(date, type, it, subtype).also {
                if (it is org.jetbrains.exposed.sql.statements.InsertStatement<*> && it.insertedCount == 0) {
                    logger.warn("Failed to insert pulse cache record for $date $type $subtype")
                }
            }
        }

    /**
     * Creates a cache record for the given type if it does not exist, or fetches the cache record
     */
    private fun fetchOrBuildCacheFromDataSource(
        type: PulseCacheType,
        subtype: String? = null,
        dataSourceFn: () -> PulseMetric
    ): PulseMetric = transaction {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        val todayCache =
            PulseCacheRecord.findByDateAndType(today, type, subtype)

        // if this is a scheduled task, bust the cache
        val bustCache = isScheduledTask()

        if (todayCache == null || bustCache) {
            val metric = dataSourceFn()
            val yesterdayMetric =
                PulseCacheRecord.findByDateAndType(
                    yesterday,
                    type,
                    subtype
                )?.data
                    ?: buildAndSavePulseMetric(
                        date = yesterday,
                        type = type,
                        previous = metric.amount,
                        current = metric.amount,
                        base = metric.base,
                        quote = metric.quote,
                        quoteAmount = metric.quoteAmount,
                        series = metric.series,
                        subtype = subtype
                    )

            buildAndSavePulseMetric(
                date = today,
                type = type,
                previous = yesterdayMetric.amount,
                current = metric.amount,
                base = metric.base,
                quote = metric.quote,
                quoteAmount = metric.quoteAmount,
                series = metric.series,
                subtype = subtype
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
            tokenService.getTokenLatest()?.takeIf { it.quote[quote] != null }
                ?.let {
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

    /**
     * Returns global market cap aka total AUM
     */
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

    /**
     * Return exchange-based trade settlement count
     */
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

    /**
     * Return exchange-based trade value settled
     */
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

    /**
     * Uses metadata module reported values to calculate receivables since
     * all data in metadata today is loan receivables
     */
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

    /**
     * Retrieves the transaction volume for the last 30 days to build
     * metric chart data
     */
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

    /**
     * Total ecosystem participants based on active accounts
     */
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
            else -> throw ResourceNotFoundException("Invalid pulse metric request for type $type")
        }
    }

    private fun isScheduledTask(): Boolean =
        Thread.currentThread().name.startsWith("scheduling-")

    /**
     * Periodically refreshes the pulse cache
     */
    fun refreshCache() = transaction {
        val threadName = Thread.currentThread().name
        logger.info("Refreshing pulse cache for thread $threadName")
        PulseCacheType.entries.filter {
            it != PulseCacheType.PULSE_ASSET_VOLUME_SUMMARY_METRIC &&
                    it != PulseCacheType.PULSE_ASSET_PRICE_SUMMARY_METRIC
        }
            .forEach { type ->
                pulseMetric(type)
            }

        PulseCacheType.entries.filter {
            it == PulseCacheType.PULSE_ASSET_VOLUME_SUMMARY_METRIC ||
                    it == PulseCacheType.PULSE_ASSET_PRICE_SUMMARY_METRIC
        }
            .forEach { type ->
                pulseAssetSummaries()
            }

        logger.info("Pulse cache refreshed for thread $threadName")
    }

    /**
     * Asset denom  metadata from chain
     */
    private fun pulseAssetDenomMetadata(denom: String) =
        assetService.getDenomMetadataSingle(denom)

    private fun denomExponent(denomMetadata: Bank.Metadata) =
        denomMetadata.denomUnitsList.firstOrNull { it.exponent != 0 }?.exponent

    private fun inversePowerOfTen(exp: Int) =
        10.0.pow(exp.toDouble() * -1).toBigDecimal()

    /**
     * Build cache of exchange-traded asset summaries using nav events from
     * exchange module for USD-based assets
     */
    private fun pulseAssetSummariesForNavEvents() =
        NavEventsRecord.getNavEvents(
            fromDate = LocalDateTime.now().minusDays(1).startOfDay()
        ).filter {
            it.source.startsWith("x/exchange") &&
                    it.priceDenom?.lowercase()
                        ?.startsWith("u$USD_LOWER") == true
        }

    /**
     * TODO - this is problematic for a number of reasons:
     *        - it's not clear how to handle assets that are not USD-based
     *        - as we turn over a new day, there are no rows in the Nav Events until exchanges trade
     *        - the market cap is based on the trade volume for the day, which is not a good metric
     *          instead, we should be using the total supply of the asset based on commitments?
     *        - this outer query on NavEvents takes 10s to run :( - perhaps pull committed assets from the chain first?
     *
     */
    fun pulseAssetSummaries(): List<PulseAssetSummary> =
        pulseAssetSummariesForNavEvents()
            .groupBy { it.denom!! }
            .map { (denom, events) ->
                val denomMetadata = pulseAssetDenomMetadata(denom)
                val denomExp = denomExponent(denomMetadata) ?: 1

                val priceMetric = fetchOrBuildCacheFromDataSource(
                    type = PulseCacheType.PULSE_ASSET_PRICE_SUMMARY_METRIC,
                    subtype = denom
                ) {
                    // TODO i'm not sure i like how this lambda captures the events object list
                    val tradeValue = events.sumOf { it.priceAmount!! }
                        .toBigDecimal()
                        .times(inversePowerOfTen(6))
                    val tradeVolume = events.sumOf { it.volume }
                        .toBigDecimal()
                        .times(inversePowerOfTen(denomExp))
                    val avgTradePrice =
                        tradeValue.divide(tradeVolume, 6, RoundingMode.HALF_UP)

                    PulseMetric.build(
                        base = USD_UPPER,
                        amount = avgTradePrice,
                    )
                }

                val volumeMetric = fetchOrBuildCacheFromDataSource(
                    type = PulseCacheType.PULSE_ASSET_VOLUME_SUMMARY_METRIC,
                    subtype = denom
                ) {
                    val tradeVolume = events.sumOf { it.volume }
                        .toBigDecimal()
                        .times(inversePowerOfTen(denomExp))

                    PulseMetric.build(
                        base = denom,
                        amount = tradeVolume
                    )
                }

                PulseAssetSummary(
                    id = UUID.randomUUID(),
                    name = denomMetadata.name,
                    description = denomMetadata.description,
                    symbol = denomMetadata.symbol,
                    base = denom,
                    quote = USD_UPPER,
                    marketCap = priceMetric.amount.times(volumeMetric.amount),
                    priceTrend = priceMetric.trend,
                    volumeTrend = volumeMetric.trend
                )
            }.sortedBy { it.symbol }
}
