package io.provenance.explorer.service

import com.fasterxml.jackson.databind.JsonNode
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import cosmos.bank.v1beta1.Bank
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN_BASE_MULTIPLIER
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.config.pulse.PulseProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.EntityNavEvent
import io.provenance.explorer.domain.entities.LedgerEntityRecord
import io.provenance.explorer.domain.entities.LedgerEntitySpecRecord
import io.provenance.explorer.domain.entities.NavEvent
import io.provenance.explorer.domain.entities.NavEventsRecord
import io.provenance.explorer.domain.entities.PulseCacheRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.roundWhole
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.pulse.EntityLedgeredAsset
import io.provenance.explorer.domain.models.explorer.pulse.EntityLedgeredAssetDetail
import io.provenance.explorer.domain.models.explorer.pulse.EntityType
import io.provenance.explorer.domain.models.explorer.pulse.ExchangeSummary
import io.provenance.explorer.domain.models.explorer.pulse.MetricProgress
import io.provenance.explorer.domain.models.explorer.pulse.MetricRangeType
import io.provenance.explorer.domain.models.explorer.pulse.MetricSeries
import io.provenance.explorer.domain.models.explorer.pulse.PulseAssetSummary
import io.provenance.explorer.domain.models.explorer.pulse.PulseCacheType
import io.provenance.explorer.domain.models.explorer.pulse.PulseLoanLedger
import io.provenance.explorer.domain.models.explorer.pulse.PulseMetric
import io.provenance.explorer.domain.models.explorer.pulse.TransactionSummary
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provenance.explorer.grpc.v1.ExchangeGrpcClient
import io.provenance.explorer.model.ValidatorState.ACTIVE
import io.provenance.explorer.model.base.DateTruncGranularity
import io.provenance.explorer.model.base.PagedResults
import io.provenance.explorer.model.base.USD_LOWER
import io.provenance.explorer.model.base.USD_UPPER
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow
/**
 * Service handler for the Provenance Pulse application
 */
@Service
class PulseMetricService(
    private val tokenService: TokenService,
    private val validatorService: ValidatorService,
    private val assetService: AssetService,
    private val explorerService: ExplorerService,
    private val exchangeGrpcClient: ExchangeGrpcClient,
    private val accountGrpcClient: AccountGrpcClient,
    private val pulseProperties: PulseProperties,
    @Qualifier("pulseHttpClient") private val pulseHttpClient: HttpClient,
    private val pricingService: PricingService
) {
    companion object {
        private val isBackfillInProgress = AtomicBoolean(false)
    }

    protected val logger = logger(PulseMetricService::class)

    private val pulseMetricCache: Cache<Triple<LocalDate, PulseCacheType, String?>, PulseMetric> =
        Caffeine.newBuilder().apply {
            maximumSize(200)
        }.build()

    private val denomMetadataCache: Cache<String, Bank.Metadata> =
        Caffeine.newBuilder().apply {
            expireAfterWrite(1, TimeUnit.HOURS)
            maximumSize(100)
        }.build()

    private val denomCurrentSupplyCache: Cache<String, BigDecimal> =
        Caffeine.newBuilder().apply {
            expireAfterWrite(30, TimeUnit.MINUTES)
            maximumSize(100)
        }.build()

    /* so it turns out that the `usd` in metadata nav events
       use 3 decimal places - :|
     */
    private val scopeNAVDecimal = inversePowerOfTen(3)

    val base = UTILITY_TOKEN
    val quote = USD_UPPER

    private val count = "COUNT"
    private val percentage = "PERCENTAGE"
    private val longest_range = 90L

    private fun nowUTC() = LocalDateTime.now(ZoneOffset.UTC)
    private fun endOfDay(time: LocalDateTime) =
        time.plusDays(1).minusSeconds(1)

    /**
     * Builds a pulse metric and saves it to the DB cache and Caffeine cache
     */
    private fun buildAndSavePulseMetric(
        date: LocalDate, type: PulseCacheType,
        previous: BigDecimal, current: BigDecimal,
        base: String,
        quote: String? = null,
        quoteAmount: BigDecimal? = null,
        series: MetricSeries? = null,
        progress: MetricProgress? = null,
        subtype: String? = null
    ) =
        PulseMetric.build(
            previous = previous,
            current = current,
            base = base,
            quote = quote,
            quoteAmount = quoteAmount,
            series = series,
            progress = progress,
        ).also {
            PulseCacheRecord.upsert(date, type, it, subtype).also { p ->
                if (p is org.jetbrains.exposed.sql.statements.InsertStatement<*> && p.insertedCount == 0) {
                    logger.warn("Failed to insert pulse cache record for $date $type $subtype")
                }
            }
            pulseMetricCache.put(Triple(date, type, subtype), it)
        }

    /**
     * Retrieve pulse metric from caffeine cache or DB cache
     */
    private fun fromPulseMetricCache(
        date: LocalDate,
        type: PulseCacheType,
        subtype: String? = null
    ): PulseMetric? =
        pulseMetricCache.get(
            Triple(
                date,
                type,
                subtype
            )
        ) { PulseCacheRecord.findByDateAndType(date, type, subtype)?.data }

    private fun rangeToDays(range: MetricRangeType) =
        when (range) {
            MetricRangeType.DAY -> 1
            MetricRangeType.WEEK -> 7
            MetricRangeType.MONTH -> 30
            MetricRangeType.QUARTER -> 90
            MetricRangeType.YEAR -> 365
        }.toLong()

    private fun backInTime(range: MetricRangeType) =
        nowUTC().minusDays(rangeToDays(range))

    /**
     * Creates a cache record for the given type if it does not exist, or fetches the cache record.
     * Pulse operates on the premise that a range of history are required to calculate the current day's metric
     * where daily history is built up from a scheduled task refreshing the metric from a functional data source.
     */
    private fun fetchOrBuildCacheFromDataSource(
        type: PulseCacheType,
        subtype: String? = null,
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null,
        dataSourceFn: () -> PulseMetric
    ): PulseMetric = transaction {
        val today = atDateTime?.toLocalDate() ?: nowUTC().toLocalDate()
        val todayCache = fromPulseMetricCache(today, type, subtype)
        // if this is a scheduled task, bust the cache
        val bustCache = isScheduledTask()
        val isBackFillAndMissing = isBackfillInProgress.get()
                && PulseCacheRecord.findByDateAndType(
            today,
            type,
            subtype
        )?.data == null

        if (todayCache == null || bustCache || isBackFillAndMissing) {
            val metric = dataSourceFn()
            val previousMetricDate = if (atDateTime != null) {
                atDateTime.minusDays(1).toLocalDate()
            } else {
                backInTime(range).toLocalDate()
            }
            val previousMetric =
                fromPulseMetricCache(
                    previousMetricDate,
                    type,
                    subtype
                )
                    ?: buildAndSavePulseMetric(
                        date = previousMetricDate,
                        type = type,
                        previous = metric.amount,
                        current = metric.amount,
                        base = metric.base,
                        quote = metric.quote,
                        quoteAmount = metric.quoteAmount,
                        series = metric.series,
                        progress = metric.progress,
                        subtype = subtype
                    )

            buildAndSavePulseMetric(
                date = today,
                type = type,
                previous = previousMetric.amount,
                current = metric.amount,
                base = metric.base,
                quote = metric.quote,
                quoteAmount = metric.quoteAmount,
                series = metric.series,
                progress = metric.progress,
                subtype = subtype
            )
        } else todayCache
    }.let {
        if (range != MetricRangeType.DAY) {
            val previous = fromPulseMetricCache(
                backInTime(range).toLocalDate(),
                type,
                subtype
            )?.amount ?: BigDecimal.ZERO
            // Trim series data to only include data within the range
            val trimmedSeries =
                it.series?.let { series ->
                    val seriesSize = series.seriesData.size
                    val days = rangeToDays(range).toInt()
                    if (seriesSize > days) {
                        val startIdx = seriesSize - days
                        MetricSeries(
                            seriesData =
                                series.seriesData.subList(
                                    startIdx,
                                    seriesSize
                                ),
                            labels =
                                series.labels.subList(
                                    startIdx,
                                    seriesSize
                                )
                        )
                    } else {
                        series
                    }
                }
            PulseMetric.build(
                previous = previous,
                current = it.amount,
                base = it.base,
                quote = it.quote,
                quoteAmount = it.quoteAmount,
                series = trimmedSeries
            )
        } else {
            val previous = fromPulseMetricCache(
                backInTime(range).toLocalDate(),
                type,
                subtype
            )?.amount ?: BigDecimal.ZERO
            // If 24h (MetricRangeType.DAY), set series to null
            if (it.series != null) {
                PulseMetric.build(
                    previous = previous,
                    current = it.amount,
                    base = it.base,
                    quote = it.quote,
                    quoteAmount = it.quoteAmount,
                    series = null
                )
            } else {
                it
            }
        }
    }

    /**
     * Use the assumption that Spring scheduled task threads begin with "scheduling-"
     * to determine if the current thread is a scheduled task
     */
    private fun isScheduledTask(): Boolean =
        Thread.currentThread().name.startsWith("scheduling-")

    private fun denomSupplyCache(
        denom: String,
        atDateTime: LocalDateTime? = null
    ) =
        if (atDateTime != null) {
            val height = BlockCacheRecord.getLastBlockBeforeTime(atDateTime)
            assetService.getCurrentSupplyAtHeight(denom, height)
                .toBigDecimal()
        } else {
            denomCurrentSupplyCache.get(denom) {
                assetService.getCurrentSupply(denom).toBigDecimal()
            }
        } ?: BigDecimal.ZERO

    private fun pulseLastTradedAssetPrice(
        denom: String,
        atDate: LocalDate = nowUTC().startOfDay().toLocalDate()
    ): BigDecimal =
        fromPulseMetricCache(
            atDate,
            PulseCacheType.PULSE_ASSET_PRICE_SUMMARY_METRIC, denom
        ).let {
            if (it?.amount != null && it.amount > BigDecimal.ZERO) {
                it.amount
            } else {
                // find latest cached price
                var giveUp = 1L
                while (giveUp++ < 15L) {
                    val amt = fromPulseMetricCache(
                        atDate.minusDays(giveUp),
                        PulseCacheType.PULSE_ASSET_PRICE_SUMMARY_METRIC, denom
                    )?.amount
                    if (amt != null && amt > BigDecimal.ZERO) {
                        return@let amt
                    }
                }
                logger.warn("Failed to find price for $denom on $atDate looking back $giveUp days")
                return@let BigDecimal.ZERO
            }
        }

    /**
     * Returns the current hash market cap metric comparing the previous range market cap
     * to the current day's market cap.
     *
     * The total market value of a cryptocurrency's circulating supply.
     * It is analogous to the free-float capitalization in the stock market.
     * Market cap = Current price x Circulating supply
     */
    private fun hashMarketCapMetric(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null,
        type: PulseCacheType
    ): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = type,
            atDateTime = atDateTime,
            range = range
        ) {
            var height: Int? = null
            if (atDateTime != null) {
                height = BlockCacheRecord.getLastBlockBeforeTime(atDateTime)
                tokenService.getTokenHistorical(
                    fromDate = atDateTime,
                    toDate = atDateTime
                ).firstOrNull { it.quote[quote] != null }?.quote?.get(quote)?.close
            } else {
                tokenService.getTokenLatest()
                    ?.takeIf { it.quote[quote] != null }?.quote?.get(quote)?.price
            }?.let { price ->
                val supply = when (type) {
                    PulseCacheType.HASH_FOUNDATION_MARKET_CAP_METRIC -> tokenService.totalBalanceForList(
                        pulseProperties.hashHoldersExcludedFromCirculatingSupply.toSet(), height
                    )

                    else -> tokenService.circulatingSupply(height, atDateTime)
                }.divide(UTILITY_TOKEN_BASE_MULTIPLIER)
                val marketCap = price.times(supply)

                PulseMetric.build(
                    base = USD_UPPER,
                    amount = marketCap
                )
            } ?: PulseMetric.build(
                base = USD_UPPER,
                amount = BigDecimal.ZERO,
            )
        }

    private fun hashTVL(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.HASH_TVL_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            val hashCommittedAmount = exchangeSummaries(UTILITY_TOKEN).sumOf { it.committed }
            val hashPrice = hashPriceAtDate(atDateTime)
            PulseMetric.build(
                base = USD_UPPER,
                amount = hashCommittedAmount.times(hashPrice)
            )
        }

    private fun pulseTVL(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_TVL_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            val committedValue = this.exchangeCommittedAssetsValue(
                range = range,
                atDateTime = atDateTime
            )
            val navValue = this.totalMetadataNavs(
                range = range,
                atDateTime = atDateTime
            )
            val privateEquityValue = this.pulseProperties.privateEquityTvlDenoms.sumOf {
                this.denomSupplyCache(it, atDateTime)
                    .times(
                        pricingService.getPricingInfoSingle(it)
                            ?: BigDecimal.ZERO
                    )
            }

            val totalValue = committedValue.amount
                .add(navValue.amount)
                .add(privateEquityValue)

            PulseMetric.build(
                base = USD_UPPER,
                amount = totalValue,
                series = seriesFromPriorMetrics(
                    type = PulseCacheType.PULSE_TVL_METRIC,
                    days = longest_range,
                    valueSelector = { it.trend?.changeQuantity ?: BigDecimal.ZERO }
                )
            )
        }

    private fun pulseTradingTVL(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_TRADING_TVL_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            val committedValue = this.exchangeCommittedAssetsValue(
                range = range,
                atDateTime = atDateTime
            )
            val tradedValue = this.pulseTradeValueSettled(
                range = range,
                atDateTime = atDateTime
            )
            PulseMetric.build(
                base = USD_UPPER,
                amount = committedValue.amount.add(tradedValue.amount)
            )
        }

    /**
     * Return exchange-based trade settlement count
     */
    private fun pulseTradesSettled(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_TRADE_SETTLEMENT_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            if (atDateTime != null) {
                NavEventsRecord.getNavEvents(
                    fromDate = atDateTime.startOfDay(),
                    toDate = endOfDay(atDateTime)
                )
            } else {
                NavEventsRecord.getNavEvents(
                    fromDate = nowUTC().startOfDay()
                )
            }.count { it.source.startsWith("x/exchange") } // gross
                .toBigDecimal().let {
                    PulseMetric.build(
                        base = count,
                        amount = it
                    )
                }
        }

    /**
     * Return exchange-based trade value settled
     */
    private fun pulseTradeValueSettled(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null,
    ): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_TRADE_VALUE_SETTLED_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            if (atDateTime != null) {
                NavEventsRecord.getNavEvents(
                    fromDate = atDateTime.startOfDay(),
                    toDate = endOfDay(atDateTime)
                )
            } else {
                NavEventsRecord.getNavEvents(
                    fromDate = nowUTC().startOfDay()
                )
            }.filter {
                it.source.startsWith("x/exchange") &&
                        it.priceDenom?.startsWith("u$USD_LOWER") == true
            } // gross, but all uusd is micro
                .sumOf { it.priceAmount!! }.toBigDecimal().let {
                    PulseMetric.build(
                        base = USD_UPPER,
                        amount = it.divide(1000000.toBigDecimal())
                    )
                }
        }

    /**
     * Uses metadata module reported values to calculate receivables since
     * all data in metadata today is loan receivables
     */
    private fun pulseTodaysNavs(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_TODAYS_NAV_METRIC,
            range = range,
            atDateTime = atDateTime
        ) { // TODO technically correct assuming only metadata nav events are receivables
            if (atDateTime != null) {
                NavEventsRecord.getNavEvents(
                    fromDate = atDateTime.startOfDay(),
                    toDate = endOfDay(atDateTime),
                    source = "metadata",
                    priceDenoms = listOf(USD_LOWER)
                )
            } else {
                NavEventsRecord.getNavEvents(
                    fromDate = nowUTC().startOfDay(),
                    source = "metadata",
                    priceDenoms = listOf(USD_LOWER)
                )
            }
                .sortedWith(compareBy<NavEvent> { it.scopeId }.thenByDescending { it.blockTime })
                .distinctBy {
                    // will keep the first occurrence which is the latest price event
                    it.scopeId
                }
                .sumOf { it.priceAmount!! }.toBigDecimal().let {
                    PulseMetric.build(
                        base = USD_UPPER,
                        amount = it.times(scopeNAVDecimal)
                    )
                }
        }

    private fun totalMetadataNavs(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_TOTAL_NAV_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            NavEventsRecord.totalMetadataNavs(atDateTime).let {
                PulseMetric.build(
                    base = USD_UPPER,
                    amount = it.times(scopeNAVDecimal)
                )
            }
        }

    /**
     * Retrieves the transaction volume for the last 30 days to build
     * metric chart data
     */
    private fun transactionVolume(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_TRANSACTION_VOLUME_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            val countForDates = TxCacheRecord.countForDates(
                daysPrior = longest_range.toInt(),
                atDateTime = atDateTime
            )
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
            val count = if (atDateTime != null) {
                TxCacheRecord.getTotalTxCountToDate(atDateTime)
            } else {
                TxCacheRecord.getTotalTxCount()
            }

            PulseMetric.build(
                base = base,
                amount = count.toBigDecimal(),
                quote = null,
                series = series
            )
        }

    /**
     * Compute the transaction volume over a range instead of the
     * total transactions up to a date (like transactionVolume does).
     */
    private fun transactionVolumeOverRange(
        range: MetricRangeType = MetricRangeType.DAY,
        type: PulseCacheType,
        atDateTime: LocalDateTime? = null
    ) = if (atDateTime != null || isScheduledTask()) {
        transactionVolume(
            range = range,
            atDateTime = atDateTime
        )
    } else {
        // populate current days metric if we haven't at this point
        if (fromPulseMetricCache(nowUTC().toLocalDate(), type) == null) {
            transactionVolume(range = range)
        }

        val spans = rangeOverRangeSpans(
            range = range,
            type = type
        )

        /* Daily transactions are the total transactions to that date
           so to get the sum over a range we must subtract the range's
           end value from its start value.
           This is also why we don't short circuit when the range is
           a DAY like we do for fees.
         */
        val rangeOverAmount = if (spans.first.size == 1) {
            spans.first.last().trend?.changeQuantity ?: BigDecimal.ZERO
        } else {
            spans.first.last().amount.minus(spans.first.first().amount)
        }
        val rangeAmount = if (spans.second.size == 1) {
            spans.second.last().trend?.changeQuantity ?: BigDecimal.ZERO
        } else {
            spans.second.last().amount.minus(spans.second.first().amount)
        }

        // Filter the series data to only include the first rangeToDays(range) entries
        val originalSeries = spans.second.last().series
        val days = rangeToDays(range).toInt()
        val filteredSeries = originalSeries?.let { series ->
            if (series.seriesData.size > days) {
                val startIdx = series.seriesData.size - days
                series.copy(
                    seriesData = series.seriesData.drop(startIdx),
                    labels = series.labels.drop(startIdx)
                )
            } else {
                series
            }
        }
        PulseMetric.build(
            previous = rangeOverAmount,
            current = rangeAmount,
            base = spans.second.first().base,
            quote = spans.second.first().quote,
            quoteAmount = spans.second.first().quoteAmount,
            series = filteredSeries,
            progress = spans.second.last().progress
        )
    }

    /**
     * Total ecosystem participants based on active accounts
     */
    private fun totalParticipants(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_PARTICIPANTS_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            AccountRecord.countActiveAccounts().let {
                PulseMetric.build(
                    base = count,
                    amount = it.toBigDecimal(),
                    series = seriesFromPriorMetrics(
                        PulseCacheType.PULSE_PARTICIPANTS_METRIC,
                        days = longest_range,
                        valueSelector = { it.trend?.changeQuantity ?: BigDecimal.ZERO }
                    ),
                )
            }
        }

    /**
     * Returns the total committed assets across all exchanges - a simple count of all commitments
     */
    private fun exchangeCommittedAssetCount(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_COMMITTED_ASSETS_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            val height = if (atDateTime != null) {
                BlockCacheRecord.getLastBlockBeforeTime(atDateTime)
            } else {
                null
            }
            runBlocking {
                exchangeGrpcClient.totalCommitmentCount(height).toBigDecimal()
            }.let {
                PulseMetric.build(
                    base = count,
                    amount = it
                )
            }
        }

    /**
     * Returns the total committed assets value across all exchanges
     * as a sum of all commitments
     */
    private fun exchangeCommittedAssetsValue(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_COMMITTED_ASSETS_VALUE_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            committedAssetTotals(atDateTime).committedAssetsToValue()
        }

    private fun Map<String, BigDecimal>.committedAssetsToValue() = this.map {
        calcExchangeTotalValueForAsset(it.key, it.value)
    }.sumOf { it }
        .let {
            PulseMetric.build(
                base = USD_UPPER,
                amount = it
            )
        }

    private fun Map<String, BigDecimal>.committedAssetsToVolume() = this.map {
        calcExchangeTotalVolumeForAsset(it.key, it.value)
    }.sumOf { it }
        .let {
            PulseMetric.build(
                base = count,
                amount = it
            )
        }

    private fun calcExchangeTotalValueForAsset(denom: String, total: BigDecimal) =
        calcExchangeTotalVolumeForAsset(denom, total).times(pulseLastTradedAssetPrice(denom))

    private fun calcExchangeTotalVolumeForAsset(denom: String, total: BigDecimal): BigDecimal {
        // convert amount to appropriate denom decimal
        var dE = denomExponent(denom)
        if (dE == 0 && denom.lowercase().contains(USD_LOWER)) {
            dE = 6
        }

        return total.times(inversePowerOfTen(dE))
    }

    private fun seriesFromPriorMetrics(
        type: PulseCacheType,
        days: Long,
        valueSelector: (PulseMetric) -> BigDecimal
    ): MetricSeries {
        val today = nowUTC().toLocalDate()
        val isTodayCached = fromPulseMetricCache(today, type) != null
        val actualDays = if (isTodayCached) days else days - 1
        val startDate = today.minusDays(days)

        val rangeSpan = rangeSpanFromCache(startDate, type, actualDays)
        val dates = (0..actualDays).map { startDate.plusDays(it).toString() }

        return MetricSeries(
            seriesData = rangeSpan.map(valueSelector),
            labels = dates
        )
    }

    private fun rangeSpanFromCache(
        startDate: LocalDate,
        type: PulseCacheType,
        daySpan: Long
    ) =
        mutableListOf<PulseMetric>().apply {
            for (i in 0..daySpan) {
                fromPulseMetricCache(startDate.plusDays(i), type)?.let {
                    this.add(it)
                } ?: throw ResourceNotFoundException(
                    "Creating $daySpan day span failed to find pulse cache record for $startDate $type."
                )
            }
        }

    /**
     * Binds the range to Range-over-Range span of time. For example,
     * given a MONTH range the metric is calculated as the sum of the last 30 days
     * values compared to the sum of the previous 30 days.
     * Or, April's amounts versus May's amounts.
     *
     * Returns a pair of prior span, current span
     */
    private fun rangeOverRangeSpans(
        range: MetricRangeType = MetricRangeType.DAY,
        type: PulseCacheType
    ): Pair<List<PulseMetric>, List<PulseMetric>> {
        val days = if (range == MetricRangeType.DAY) {
            // by default daily metrics contain curr/prev span
            0
        } else {
            rangeToDays(range)
        }
        val startDate = nowUTC().minusDays(days).toLocalDate()
        val rangeOverStartDate = startDate.minusDays(days + 1)

        val rangeSpan = rangeSpanFromCache(startDate, type, days)
        val rangeOverSpan =
            rangeSpanFromCache(rangeOverStartDate, type, days)

        return Pair(rangeOverSpan, rangeSpan)
    }

    private fun pulseChainFeesFromCache(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_CHAIN_FEES_VALUE_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            val atStart = (atDateTime ?: nowUTC()).startOfDay()
            val atEnd = endOfDay(atStart)

            val hashFees = explorerService.getGasVolume(
                atStart,
                atEnd,
                DateTruncGranularity.DAY
            ).sumOf { it.feeAmount }
                .divide(UTILITY_TOKEN_BASE_MULTIPLIER)

            val feePrice = hashFees.times(hashPriceAtDate(atDateTime))

            PulseMetric.build(
                base = USD_UPPER,
                amount = feePrice
            )
        }

    /**
     * Compute fee metrics over a range rather than from the
     * default 1 day cache
     */
    private fun pulseChainFeesOverRange(
        range: MetricRangeType,
        type: PulseCacheType,
        atDateTime: LocalDateTime?
    ) =
        if (atDateTime != null || isScheduledTask() || range == MetricRangeType.DAY) {
            pulseChainFeesFromCache(range, atDateTime)
        } else {
            val spans = rangeOverRangeSpans(
                range = range,
                type = type
            )
            val rangeOverAmount = spans.first.sumOf { it.amount }
            val rangeAmount = spans.second.sumOf { it.amount }

            PulseMetric.build(
                previous = rangeOverAmount,
                current = rangeAmount,
                base = spans.second.first().base,
                quote = spans.second.first().quote,
                quoteAmount = spans.second.first().quoteAmount,
                series = spans.second.last().series,
                progress = spans.second.last().progress
            )
        }

    /**
     * Loan-based Metrics
     */
    private fun loanLedgerUrl(
        endpoint: String,
        atDateTime: LocalDateTime? = null
    ) =
        if (atDateTime != null) {
            "${pulseProperties.loanLedgerDataUrl}/$endpoint?atDate=${
                atDateTime.toLocalDate().format(
                    DateTimeFormatter.ISO_LOCAL_DATE
                )
            }"
        } else {
            "${pulseProperties.loanLedgerDataUrl}/$endpoint"
        }

    private fun getLoanLedger(
        endpoint: String,
        atDateTime: LocalDateTime? = null
    ) =
        runBlocking {
            try {
                loanLedgerUrl(endpoint, atDateTime).let {
                    pulseHttpClient.get {
                        url(it)
                    }.body<List<PulseLoanLedger>>()
                }
            } catch (e: Exception) {
                logger.error("Failed to fetch loan ledger data: ${e.message}")
                emptyList()
            }
        }

    private fun loanLedgerEffectiveDateFilter(atDateTime: LocalDateTime? = null) =
        if (atDateTime != null) {
            atDateTime.startOfDay()
        } else {
            nowUTC().startOfDay()
        }

    private fun loanLedgerTotalBalance(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ) =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.LOAN_LEDGER_TOTAL_BALANCE_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            runBlocking {
                pulseHttpClient.get {
                    url(loanLedgerUrl("balances", atDateTime))
                }.body<JsonNode>().let {
                    PulseMetric.build(
                        base = USD_UPPER,
                        amount = it["TotalBalance"].asText().toBigDecimal()
                    )
                }
            }
        }

    private fun loanLedgerTotalCount(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ) =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.LOAN_LEDGER_TOTAL_COUNT_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            runBlocking {
                pulseHttpClient.get {
                    url(loanLedgerUrl("balances", atDateTime))
                }.body<JsonNode>().let {
                    PulseMetric.build(
                        base = count,
                        amount = it["TotalCount"].asText().toBigDecimal()
                    )
                }
            }
        }

    private fun loanLedgerPaymentsOverRange(
        range: MetricRangeType,
        type: PulseCacheType = PulseCacheType.LOAN_LEDGER_PAYMENTS_METRIC,
        atDateTime: LocalDateTime? = null
    ) =
        if (atDateTime != null || isScheduledTask() || range == MetricRangeType.DAY) {
            // For specific dates, scheduled tasks, or daily range, use the original function
            loanLedgerPayments(range, atDateTime)
        } else {
            // For range comparisons, populate current day's metric if missing
            if (fromPulseMetricCache(nowUTC().toLocalDate(), type) == null) {
                loanLedgerPayments(range = range)
            }

            val spans = rangeOverRangeSpans(range = range, type = type)

            // Calculate the sum over each range period
            val rangeOverAmount = spans.first.sumOf { it.amount }
            val rangeAmount = spans.second.sumOf { it.amount }

            PulseMetric.build(
                previous = rangeOverAmount,
                current = rangeAmount,
                base = spans.second.first().base,
                quote = spans.second.first().quote,
                quoteAmount = spans.second.first().quoteAmount,
                series = spans.second.last().series,
                progress = spans.second.last().progress
            )
        }

    private fun loanLedgerPayments(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ) =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.LOAN_LEDGER_PAYMENTS_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            getLoanLedger("payments", atDateTime)
                .filter {
                    it.effectiveDate.startOfDay() == loanLedgerEffectiveDateFilter(
                        atDateTime
                    )
                }
                .sumOf { it.entryAmount }.let {
                    PulseMetric.build(
                        base = USD_UPPER,
                        amount = it.toBigDecimal()
                    )
                }
        }

    private fun loanLedgerTotalPaymentsOverRange(
        range: MetricRangeType,
        type: PulseCacheType = PulseCacheType.LOAN_LEDGER_TOTAL_PAYMENTS_METRIC,
        atDateTime: LocalDateTime? = null
    ) =
        if (atDateTime != null || isScheduledTask() || range == MetricRangeType.DAY) {
            // For specific dates, scheduled tasks, or daily range, use the original function
            loanLedgerTotalPayments(range, atDateTime)
        } else {
            // For range comparisons, populate current day's metric if missing
            if (fromPulseMetricCache(nowUTC().toLocalDate(), type) == null) {
                loanLedgerTotalPayments(range = range)
            }

            val spans = rangeOverRangeSpans(
                range = range,
                type = type
            )

            // Calculate the sum of payment counts over each range period
            val rangeOverAmount = spans.first.sumOf { it.amount }
            val rangeAmount = spans.second.sumOf { it.amount }

            PulseMetric.build(
                previous = rangeOverAmount,
                current = rangeAmount,
                base = spans.second.first().base,
                quote = spans.second.first().quote,
                quoteAmount = spans.second.first().quoteAmount,
                series = spans.second.last().series,
                progress = spans.second.last().progress
            )
        }

    private fun loanLedgerTotalPayments(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ) =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.LOAN_LEDGER_TOTAL_PAYMENTS_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            getLoanLedger("payments", atDateTime).count {
                it.effectiveDate.startOfDay() == loanLedgerEffectiveDateFilter(
                    atDateTime
                )
            }.let {
                PulseMetric.build(
                    base = count,
                    amount = it.toBigDecimal()
                )
            }
        }

    private fun loanLedgerDisbursementsOverRange(
        range: MetricRangeType,
        type: PulseCacheType = PulseCacheType.LOAN_LEDGER_DISBURSEMENTS_METRIC,
        atDateTime: LocalDateTime? = null
    ) =
        if (atDateTime != null || isScheduledTask() || range == MetricRangeType.DAY) {
            // For specific dates, scheduled tasks, or daily range, use the original function
            loanLedgerDisbursements(range, atDateTime)
        } else {
            // For range comparisons, populate current day's metric if missing
            if (fromPulseMetricCache(nowUTC().toLocalDate(), type) == null) {
                loanLedgerDisbursements(range = range)
            }

            val spans = rangeOverRangeSpans(
                range = range,
                type = type
            )

            // Calculate the sum of disbursement amounts over each range period
            val rangeOverAmount = spans.first.sumOf { it.amount }
            val rangeAmount = spans.second.sumOf { it.amount }

            PulseMetric.build(
                previous = rangeOverAmount,
                current = rangeAmount,
                base = spans.second.first().base,
                quote = spans.second.first().quote,
                quoteAmount = spans.second.first().quoteAmount,
                series = spans.second.last().series,
                progress = spans.second.last().progress
            )
        }

    private fun loanLedgerDisbursements(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ) =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.LOAN_LEDGER_DISBURSEMENTS_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            getLoanLedger("disbursements", atDateTime)
                .filter {
                    it.effectiveDate.startOfDay() == loanLedgerEffectiveDateFilter(
                        atDateTime
                    )
                }
                .sumOf { it.entryAmount }.let {
                    PulseMetric.build(
                        base = USD_UPPER,
                        amount = it.toBigDecimal()
                    )
                }
        }

    private fun loanLedgerDisbursementCountOverRange(
        range: MetricRangeType,
        type: PulseCacheType = PulseCacheType.LOAN_LEDGER_DISBURSEMENT_COUNT_METRIC,
        atDateTime: LocalDateTime? = null
    ) =
        if (atDateTime != null || isScheduledTask() || range == MetricRangeType.DAY) {
            // For specific dates, scheduled tasks, or daily range, use the original function
            loanLedgerDisbursementCount(range, atDateTime)
        } else {
            // For range comparisons, populate current day's metric if missing
            if (fromPulseMetricCache(nowUTC().toLocalDate(), type) == null) {
                loanLedgerDisbursementCount(range = range)
            }

            val spans = rangeOverRangeSpans(
                range = range,
                type = type
            )

            // Calculate the sum of disbursement counts over each range period
            val rangeOverAmount = spans.first.sumOf { it.amount }
            val rangeAmount = spans.second.sumOf { it.amount }

            PulseMetric.build(
                previous = rangeOverAmount,
                current = rangeAmount,
                base = spans.second.first().base,
                quote = spans.second.first().quote,
                quoteAmount = spans.second.first().quoteAmount,
                series = spans.second.last().series,
                progress = spans.second.last().progress
            )
        }
    private fun loanLedgerDisbursementCount(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ) =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.LOAN_LEDGER_DISBURSEMENT_COUNT_METRIC,
            range = range,
            atDateTime = atDateTime
        ) {
            getLoanLedger("disbursements", atDateTime)
                .count {
                    it.effectiveDate.startOfDay() == loanLedgerEffectiveDateFilter(
                        atDateTime
                    )
                }.let {
                    PulseMetric.build(
                        base = count,
                        amount = it.toBigDecimal()
                    )
                }
        }

    /**
     * metrics for known entities
     * waterfalls to calc for totals across all entities, totals by entity type, and finally by individual entity
     */

    private fun entityTotalBalance(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null,
    ) =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.ENTITY_TOTAL_BALANCE_METRIC,
            range = range,
            atDateTime = atDateTime,
        ) {
            EntityType.entries.sumOf {
                totalBalanceByEntityType(range, it, atDateTime).amount
            }.let {
                PulseMetric.build(
                    base = USD_UPPER,
                    amount = it
                )
            }
        }

    private fun entityTotalAssets(
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null,
    ) =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.ENTITY_TOTAL_ASSETS_METRIC,
            range = range,
            atDateTime = atDateTime,
        ) {
            EntityType.entries.sumOf {
                totalAssetsByEntityType(range, it, atDateTime).amount
            }.let {
                PulseMetric.build(
                    base = count,
                    amount = it
                )
            }
        }

    private fun totalBalanceByEntityType(
        range: MetricRangeType = MetricRangeType.DAY,
        type: EntityType,
        atDateTime: LocalDateTime? = null,
    ) =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.ENTITY_TOTAL_BALANCE_METRIC,
            range = range,
            atDateTime = atDateTime,
            subtype = type.name,
        ) {
            LedgerEntityRecord.findByType(type).sumOf {
                totalBalanceByEntity(range, it, atDateTime).amount
            }.let {
                PulseMetric.build(
                    base = USD_UPPER,
                    amount = it
                )
            }
        }

    private fun totalAssetsByEntityType(
        range: MetricRangeType = MetricRangeType.DAY,
        type: EntityType,
        atDateTime: LocalDateTime? = null,
    ) =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.ENTITY_TOTAL_ASSETS_METRIC,
            range = range,
            atDateTime = atDateTime,
            subtype = type.name,
        ) {
            LedgerEntityRecord.findByType(type).sumOf {
                totalAssetsByEntity(range, it, atDateTime).amount
            }.let {
                PulseMetric.build(
                    base = count,
                    amount = it
                )
            }
        }

    private fun totalBalanceByEntity(
        range: MetricRangeType = MetricRangeType.DAY,
        entity: LedgerEntityRecord,
        atDateTime: LocalDateTime? = null,
    ) =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.ENTITY_TOTAL_BALANCE_METRIC,
            range = range,
            atDateTime = atDateTime,
            subtype = entity.uuid,
        ) {
            when (entity.type) {
                EntityType.LOANS -> loanLedgerTotalBalance(MetricRangeType.DAY)
                EntityType.INSURANCE_POLICIES -> getNavEventsForEntity(entity, atDateTime).sumOf {
                    it.calculatePrice(entity.usdPricingExponent)
                }.let {
                    PulseMetric.build(
                        base = USD_UPPER,
                        amount = it
                    )
                }
                EntityType.EXCHANGE -> marketTotalValue(entity.marketId!!)
            }
        }

    private fun totalAssetsByEntity(
        range: MetricRangeType = MetricRangeType.DAY,
        entity: LedgerEntityRecord,
        atDateTime: LocalDateTime? = null,
    ) =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.ENTITY_TOTAL_ASSETS_METRIC,
            range = range,
            atDateTime = atDateTime,
            subtype = entity.uuid,
        ) {
            when (entity.type) {
                EntityType.LOANS -> loanLedgerTotalCount(MetricRangeType.DAY)
                EntityType.INSURANCE_POLICIES -> getNavEventsForEntity(entity, atDateTime).size.let {
                    PulseMetric.build(base = count, amount = it.toBigDecimal())
                }
                EntityType.EXCHANGE -> marketTotalVolume(entity.marketId!!)
            }
        }

    private fun getNavEventsForEntity(
        entity: LedgerEntityRecord,
        atDateTime: LocalDateTime? = null,
        limit: Int? = null,
        offset: Int? = null
    ) = NavEventsRecord.latestScopeNavsByEntity(
        entity = entity,
        specificationIds = LedgerEntitySpecRecord.findByUuid(entity.uuid).map { it.specificationId },
        fromDate = atDateTime,
        limit = limit,
        offset = offset
    )

    private fun EntityNavEvent.calculatePrice(pricingExponent: Int?): BigDecimal {
        val exponent = pricingExponent ?: 6.takeIf { priceDenom.startsWith("uusd") } ?: 0
        return priceAmount.toBigDecimal().times(inversePowerOfTen(exponent))
    }

    private fun marketCommittedAssets(marketId: Int) = runBlocking {
        exchangeGrpcClient.marketTotalCommittedAssets(marketId)
            .flatten()
            .groupBy { it.first }.mapValues { it.value.sumOf { v -> v.second } }
    }

    private fun marketTotalValue(marketId: Int) = marketCommittedAssets(marketId).committedAssetsToValue()

    private fun marketTotalVolume(marketId: Int) = marketCommittedAssets(marketId).committedAssetsToVolume()

    /**
     * Asset denom  metadata from chain
     */
    private fun pulseAssetDenomMetadata(denom: String) =
        denomMetadataCache.get(denom) {
            assetService.getDenomMetadataSingle(denom)
        }!!

    /**
     * Returns the exponent for the given denom metadata
     */
    private fun denomExponent(denomMetadata: Bank.Metadata) =
        denomMetadata.denomUnitsList.firstOrNull { it.exponent != 0 }?.exponent

    private fun denomExponent(denom: String) =
        denomExponent(pulseAssetDenomMetadata(denom)) ?: 0

    /**
     * Returns the inverse power of ten for the given exponent because I
     * mostly don't like to divide to move decimal places
     */
    private fun inversePowerOfTen(exp: Int) =
        10.0.pow(exp.toDouble() * -1).toBigDecimal()

    /**
     * Build cache of exchange-traded asset summaries using nav events from
     * exchange module for USD-based assets
     */
    private fun pulseAssetSummariesForNavEvents(
        denom: String,
        source: String? = null,
        atDateTime: LocalDateTime? = null
    ) =
        if (atDateTime != null) {
            NavEventsRecord.getNavEvents(
                denom = denom,
                fromDate = atDateTime.startOfDay(),
                toDate = endOfDay(atDateTime),
                source = source
            )
        } else {
            NavEventsRecord.getNavEvents(
                denom = denom,
                fromDate = nowUTC().startOfDay(),
                source = source
            )
        }.filter { e ->
            (source != null || e.source.startsWith("x/exchange")) &&
                    e.priceDenom?.lowercase()
                        ?.startsWith("u$USD_LOWER") == true
        }

    /**
     * Returns the total committed assets across all exchanges
     */
    private fun committedAssetTotals(atDateTime: LocalDateTime? = null) =
        runBlocking {
            if (atDateTime != null) {
                BlockCacheRecord.getLastBlockBeforeTime(atDateTime)
            } else {
                null
            }.let {
                exchangeGrpcClient.totalCommittedAssetTotals(it)
            }
        }

    private fun hashPriceAtDate(atDateTime: LocalDateTime?) =
        if (atDateTime != null) {
            tokenService.getTokenHistorical(atDateTime, atDateTime)
                .firstOrNull { it.quote[USD_UPPER] != null }
                ?.let {
                    it.quote[USD_UPPER]?.close
                }
        } else {
            tokenService.getTokenLatest()?.quote?.get(USD_UPPER)?.price
        } ?: BigDecimal.ZERO

    /**
     * Periodically refreshes the pulse cache
     */
    fun refreshCache() = if (isBackfillInProgress.get()) {
        logger.info("Skipping refreshing pulse cache because backfill is in progress.")
    } else {
        transaction {
            val threadName = Thread.currentThread().name
            logger.info("Refreshing pulse cache for thread $threadName")
            PulseCacheType.entries.filter {
                it != PulseCacheType.PULSE_ASSET_VOLUME_SUMMARY_METRIC &&
                        it != PulseCacheType.PULSE_ASSET_PRICE_SUMMARY_METRIC
            }
                .forEach { type ->
                    pulseMetric(type = type)
                }

            pulseAssetSummaries()

            logger.info("Pulse cache refreshed for thread $threadName")
        }
    }

    /**
     * Returns the current hash metrics for the given type
     */
    fun hashMetric(
        type: PulseCacheType, bustCache: Boolean = false,
        range: MetricRangeType = MetricRangeType.DAY,
        atDateTime: LocalDateTime? = null
    ) =
        fetchOrBuildCacheFromDataSource(
            type = type,
            range = range,
            atDateTime = atDateTime
        ) {
            val height = if (atDateTime != null) {
                BlockCacheRecord.getLastBlockBeforeTime(atDateTime)
            } else {
                null
            }

            val hashPriceAtDate = hashPriceAtDate(atDateTime)

            when (type) {
                PulseCacheType.HASH_STAKED_METRIC -> {
                    val staked =
                        if (atDateTime != null) {
                            runBlocking {
                                accountGrpcClient.getTotalValidatorDelegations(
                                    height
                                )
                            }
                        } else {
                            // I bet you're thinking "why not use the grpc client?", me too and i wrote this
                            validatorService.getStakingValidators(ACTIVE)
                                .sumOf { it.tokenCount }
                        }
                            .divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole()

                    val supply = tokenService.maxSupply()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER)

                    val percentageStaked = staked.divide(supply)
                        .multiply(BigDecimal(100))

                    PulseMetric.build(
                        base = percentage,
                        amount = percentageStaked
                    )
                }

                PulseCacheType.HASH_CIRCULATING_METRIC -> {
                    val tokenSupply =
                        tokenService.circulatingSupply(height, atDateTime)
                            .divide(UTILITY_TOKEN_BASE_MULTIPLIER)
                            .roundWhole()
                    val maxSupply = tokenService.maxSupply()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER)

                    val progress = MetricProgress(
                        percentage = tokenSupply.divide(maxSupply)
                            .multiply(BigDecimal(100)),
                        description = "in Circulation"
                    )

                    PulseMetric.build(
                        base = UTILITY_TOKEN,
                        amount = tokenSupply,
                        quote = USD_UPPER,
                        quoteAmount = hashPriceAtDate.times(tokenSupply),
                        progress = progress
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

                PulseCacheType.HASH_FOUNDATION_SUPPLY_METRIC -> {
                    val foundationSupply = tokenService.totalBalanceForList(
                        pulseProperties.hashHoldersExcludedFromCirculatingSupply.toSet(), height
                    ).divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole()
                    PulseMetric.build(
                        base = UTILITY_TOKEN,
                        amount = foundationSupply
                    )
                }

                PulseCacheType.HASH_VOLUME_METRIC -> {
                    if (atDateTime != null) {
                        tokenService.getTokenHistorical(atDateTime, atDateTime)
                            .firstOrNull()?.quote?.get(USD_UPPER)?.volume
                    } else {
                        tokenService.getTokenLatest()?.quote?.get(USD_UPPER)?.volume_24h
                    }.let {
                        PulseMetric.build(base = USD_UPPER, amount = it ?: BigDecimal.ZERO)
                    }
                }

                PulseCacheType.HASH_FDV_METRIC -> {
                    /*
                    The market cap if the max supply was in circulation.
                    Fully-diluted value (FDV) = price x max supply.
                     */
                    val tokenSupply = tokenService.maxSupply(height)
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER)
                        .roundWhole()
                    val fdv = hashPriceAtDate.times(tokenSupply)
                    PulseMetric.build(
                        base = USD_UPPER,
                        amount = fdv
                    )
                }

                PulseCacheType.HASH_PRICE_METRIC -> {
                    PulseMetric.build(
                        base = USD_UPPER,
                        amount = hashPriceAtDate
                    )
                }

                else -> throw ResourceNotFoundException("Invalid hash metric request for type $type")
            }
        }

    /**
     * Returns the pulse metric for the given type - pulse metrics are "global"
     * metrics that are not specific to Hash
     */
    fun pulseMetric(
        range: MetricRangeType = MetricRangeType.DAY,
        type: PulseCacheType,
        atDateTime: LocalDateTime? = null,
    ): PulseMetric {
        return when (type) {
            PulseCacheType.HASH_MARKET_CAP_METRIC,
            PulseCacheType.HASH_FOUNDATION_MARKET_CAP_METRIC -> hashMarketCapMetric(
                range,
                atDateTime,
                type
            )

            PulseCacheType.HASH_TVL_METRIC -> hashTVL(range, atDateTime)

            PulseCacheType.HASH_STAKED_METRIC,
            PulseCacheType.HASH_CIRCULATING_METRIC,
            PulseCacheType.HASH_SUPPLY_METRIC,
            PulseCacheType.HASH_FOUNDATION_SUPPLY_METRIC,
            PulseCacheType.HASH_VOLUME_METRIC,
            PulseCacheType.HASH_PRICE_METRIC,
            PulseCacheType.HASH_FDV_METRIC -> hashMetric(
                range = range,
                type = type,
                atDateTime = atDateTime
            )

            PulseCacheType.PULSE_TVL_METRIC -> pulseTVL(range, atDateTime)
            PulseCacheType.PULSE_CHAIN_FEES_VALUE_METRIC ->
                pulseChainFeesOverRange(range, type, atDateTime)

            PulseCacheType.PULSE_TRANSACTION_VOLUME_METRIC ->
                transactionVolumeOverRange(range, type, atDateTime)

            PulseCacheType.PULSE_TODAYS_NAV_METRIC -> pulseTodaysNavs(
                range,
                atDateTime
            )

            PulseCacheType.PULSE_TOTAL_NAV_METRIC -> totalMetadataNavs(
                range,
                atDateTime
            )

            PulseCacheType.PULSE_TRADE_SETTLEMENT_METRIC -> pulseTradesSettled(
                range,
                atDateTime
            )

            PulseCacheType.PULSE_TRADE_VALUE_SETTLED_METRIC -> pulseTradeValueSettled(
                range,
                atDateTime
            )

            PulseCacheType.PULSE_PARTICIPANTS_METRIC -> totalParticipants(
                range,
                atDateTime
            )

            PulseCacheType.PULSE_COMMITTED_ASSETS_METRIC -> exchangeCommittedAssetCount(
                range,
                atDateTime
            )

            PulseCacheType.PULSE_COMMITTED_ASSETS_VALUE_METRIC -> exchangeCommittedAssetsValue(
                range,
                atDateTime
            )
            /* Order of this kind of matters since it depends on
             * the committed assets value metric
             */
            PulseCacheType.PULSE_TRADING_TVL_METRIC -> pulseTradingTVL(
                range,
                atDateTime
            )

            PulseCacheType.LOAN_LEDGER_PAYMENTS_METRIC -> loanLedgerPaymentsOverRange(
                range,
                type,
                atDateTime
            )

            PulseCacheType.LOAN_LEDGER_TOTAL_PAYMENTS_METRIC -> loanLedgerTotalPaymentsOverRange(
                range,
                type,
                atDateTime
            )

            PulseCacheType.LOAN_LEDGER_TOTAL_BALANCE_METRIC -> loanLedgerTotalBalance(
                range,
                atDateTime
            )

            PulseCacheType.LOAN_LEDGER_TOTAL_COUNT_METRIC -> loanLedgerTotalCount(
                range,
                atDateTime
            )

            PulseCacheType.LOAN_LEDGER_DISBURSEMENTS_METRIC -> loanLedgerDisbursementsOverRange(
                range,
                type,
                atDateTime
            )

            PulseCacheType.LOAN_LEDGER_DISBURSEMENT_COUNT_METRIC -> loanLedgerDisbursementCountOverRange(
                range,
                type,
                atDateTime
            )

            PulseCacheType.ENTITY_TOTAL_BALANCE_METRIC -> entityTotalBalance(
                range = range,
                atDateTime = atDateTime,
            )

            PulseCacheType.ENTITY_TOTAL_ASSETS_METRIC -> entityTotalAssets(
                range = range,
                atDateTime = atDateTime,
            )

            else -> throw ResourceNotFoundException("Invalid pulse metric request for type $type")
        }
    }

    fun pulseMetricHistory(type: PulseCacheType, fromDate: LocalDate, toDate: LocalDate) =
        PulseCacheRecord.findByDateSpanAndType(fromDate, toDate, type).associateBy({ it.cacheDate }, { it.data })

    /**
     * TODO - this is problematic because it assumes all assets are USD quoted
     */
    fun pulseAssetSummaries(atDateTime: LocalDateTime? = null): List<PulseAssetSummary> =
        committedAssetTotals(atDateTime).keys.distinct().map { denom ->
            val denomMetadata = pulseAssetDenomMetadata(denom)
            val denomExp = denomExponent(denomMetadata) ?: 1
            val denomPow = inversePowerOfTen(denomExp)
            val usdPow = inversePowerOfTen(6)
            val priceMetric = fetchOrBuildCacheFromDataSource(
                type = PulseCacheType.PULSE_ASSET_PRICE_SUMMARY_METRIC,
                subtype = denom,
                atDateTime = atDateTime
            ) {
                val events = pulseAssetSummariesForNavEvents(
                    denom = denom,
                    atDateTime = atDateTime
                )
                val tradeValue = events.sumOf { it.priceAmount!! }
                    .toBigDecimal()
                    .times(usdPow)
                val tradeVolume = events.sumOf { it.volume }
                    .toBigDecimal()
                    .times(denomPow)
                val avgTradePrice =
                    if (tradeVolume.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO else
                        tradeValue.divide(tradeVolume, 6, RoundingMode.HALF_UP)

                PulseMetric.build(
                    base = USD_UPPER,
                    amount = avgTradePrice,
                )
            }
            val volumeMetric = fetchOrBuildCacheFromDataSource(
                type = PulseCacheType.PULSE_ASSET_VOLUME_SUMMARY_METRIC,
                subtype = denom,
                atDateTime = atDateTime
            ) {
                val events = pulseAssetSummariesForNavEvents(
                    denom = denom,
                    atDateTime = atDateTime
                )

                val tradeValue = events.sumOf { it.priceAmount!! }
                    .toBigDecimal()
                    .times(usdPow)

                PulseMetric.build(
                    base = USD_UPPER,
                    amount = tradeValue
                )
            }
            val supply =
                denomSupplyCache(denom = denom, atDateTime = atDateTime)
                    .times(denomPow)
            val marketCap = supply
                .times(priceMetric.amount)

            // TODO a gross assumption using USD_UPPER but will suffice for now
            PulseAssetSummary(
                id = UUID.randomUUID(),
                name = denomMetadata.name,
                description = denomMetadata.description,
                symbol = denomMetadata.symbol,
                display = denomMetadata.display,
                base = denom,
                quote = USD_UPPER,
                marketCap = marketCap,
                supply = supply,
                priceTrend = priceMetric.trend,
                volumeTrend = volumeMetric.trend
            )
        }.sortedWith(
            compareBy(
                { it.symbol.isEmpty() },
                { it.symbol }
            )
        ) // empties to the bottom

    fun exchangeSummaries(denom: String): List<ExchangeSummary> = runBlocking {
        exchangeGrpcClient.getMarketBriefsByDenom(denom)
    }.map {
        val denomMetadata = pulseAssetDenomMetadata(denom)
        val denomExp = denomExponent(denomMetadata) ?: 1
        val denomPow = inversePowerOfTen(denomExp)

        val (commitments, _) = runBlocking {
            val commitmentsDeferred =
                async { exchangeGrpcClient.getMarketCommitments(it.marketId) }
            val marketDeferred =
                async { exchangeGrpcClient.getMarket(it.marketId) }
            commitmentsDeferred.await() to marketDeferred.await()
        }

        val denomCommittedAmount = commitments.map { commitment ->
            commitment.amountList.filter { amount ->
                amount.denom == denom
            }.sumOf { s -> s.amount.toBigDecimal() }
        }.sumOf { s -> s }
            .times(denomPow)

        val eventScope = "x/exchange market ${it.marketId}"
        val events = pulseAssetSummariesForNavEvents(denom, eventScope)
        // seems backwards, but settlements is the volume of the denom settled
        val settlement = events.sumOf { e -> e.volume }
            .toBigDecimal()
            .times(denomPow)
        // and volume is the value of the denom settled
        val volume = events.sumOf { e -> e.priceAmount!! }
            .toBigDecimal()
            .times(inversePowerOfTen(6))
        // TODO probably need to use this instead of hard code USD_UPPER: market.intermediaryDenom,
        ExchangeSummary(
            id = UUID.randomUUID(),
            marketAddress = it.marketAddress,
            name = it.marketDetails.name,
            symbol = denomMetadata.symbol,
            display = denomMetadata.display,
            description = it.marketDetails.description,
            iconUri = it.marketDetails.iconUri,
            websiteUrl = it.marketDetails.websiteUrl,
            base = denomMetadata.base,
            quote = USD_UPPER,
            committed = denomCommittedAmount,
            volume = volume,
            settlement = settlement
        )
    }

    /**
     * Retrieve pageable transaction summaries for transactions that have exchanged
     * bank or exchange value by denom
     */
    fun transactionSummaries(
        denom: String,
        count: Int,
        page: Int,
        sort: List<SortOrder>,
        sortColumn: List<String>
    ): PagedResults<TransactionSummary> =
        TxCacheRecord.pulseTransactionsWithValue(
            denom,
            nowUTC().minusDays(1).startOfDay(),
            page,
            count,
            sort,
            sortColumn
        ).let { pr ->
            val denomMetadata = pulseAssetDenomMetadata(denom)
            val denomExp = denomExponent(denomMetadata) ?: 1
            val denomPow = inversePowerOfTen(denomExp)
            val denomPrice = pulseLastTradedAssetPrice(denom)

            PagedResults(
                pages = pr.pages,
                results = pr.results.map { tx ->
                    val denomTotal =
                        if (tx["denom_total"] != null)
                            BigDecimal(tx["denom_total"].toString()).times(
                                denomPow
                            )
                        else
                            BigDecimal.ZERO

                    val denomQuoteValue = denomTotal.times(denomPrice)
                    TransactionSummary(
                        txHash = tx["hash"].toString(),
                        block = tx["height"] as Int,
                        time = tx["tx_timestamp"].toString(),
                        type = tx["type"] as String,
                        value = denomTotal,
                        quoteValue = denomQuoteValue,
                        quoteDenom = USD_UPPER,
                        details = emptyList()
                    )
                },
                total = pr.total

            )
        }

    /**
     * Fill in the pulse cache for a date range for a list of cache types
     */
    fun backFillAllMetrics(
        fromDate: LocalDate,
        toDate: LocalDate,
        types: List<PulseCacheType>
    ) {
        if (isBackfillInProgress.compareAndSet(false, true)) {
            try {
                val days = fromDate.until(toDate, ChronoUnit.DAYS)
                for (i in 0..days) {
                    /*
                     Pulse works on the principal that the metric for a given
                     is the aggregation of all events for that date. So we need to
                     set the backfill date the end of the day to ensure that we
                     capture all events for that date.
                     */
                    val d = endOfDay(fromDate.plusDays(i).atStartOfDay())
                    for (type in types) {
                        try {
                            logger.info("Backfilling $type for $d")
                            if (type == PulseCacheType.PULSE_ASSET_PRICE_SUMMARY_METRIC ||
                                type == PulseCacheType.PULSE_ASSET_VOLUME_SUMMARY_METRIC
                            ) {
                                pulseAssetSummaries(d) // first to set prices
                            } else {
                                pulseMetric(
                                    type = type,
                                    atDateTime = d
                                )
                            }
                        } catch (e: Exception) {
                            logger.warn(
                                "Failed to backfill $type for $d: ${e.message}",
                                e
                            )
                        }
                    }
                }
            } finally {
                isBackfillInProgress.set(false)
            }
        } else {
            logger.warn("Backfill already in progress, skipping")
        }
    }

    /* **********************
     * Ledger Based Services
     * **********************/
    fun ledgeredAssetsByEntity(
        uuid: String,
    ): EntityLedgeredAsset = LedgerEntityRecord.findByUuid(uuid)?.toEntityLedgeredAsset()
        ?: throw ResourceNotFoundException("Entity not found for id: $uuid")

    fun ledgeredAssetsByEntity(
        count: Int,
        page: Int,
        sort: List<SortOrder>,
        sortColumn: List<String>,
    ): PagedResults<EntityLedgeredAsset> {
        val entityLedgeredAssetList = LedgerEntityRecord.getAllPaginated(page.toOffset(count), count)
            .map { it.toEntityLedgeredAsset() }

        val totalEntities = transaction { LedgerEntityRecord.all().count() }
        return PagedResults(
            pages = totalEntities.pageCountOfResults(count),
            results = entityLedgeredAssetList,
            total = totalEntities
        )
    }

    fun ledgeredAssetListByEntity(
        uuid: String,
        count: Int,
        page: Int,
        sort: List<SortOrder>,
        sortColumn: List<String>,
        ): PagedResults<EntityLedgeredAssetDetail>? {
        val entity = LedgerEntityRecord.findByUuid(uuid)
            ?: throw ResourceNotFoundException("Entity not found for id: $uuid")

        val entityAssets: List<EntityLedgeredAssetDetail> = when (entity.type) {
            // TODO should loan details be pulled from the ledger module? Using NAV for now
            EntityType.LOANS, EntityType.INSURANCE_POLICIES -> getNavEventsForEntity(
                entity = entity, limit = count, offset = page.toOffset(count)
            ).map {
                EntityLedgeredAssetDetail(
                    scopeId = it.scopeId,
                    denom = it.denom,
                    amount = it.calculatePrice(entity.usdPricingExponent),
                    base = USD_UPPER,
                    valueOwnerAddress = it.valueOwnerAddress,
                    valueOwnerDenom = it.markerDenom
                )
            }
            EntityType.EXCHANGE -> marketCommittedAssets(entity.marketId!!).map {
                EntityLedgeredAssetDetail(
                    denom = it.key,
                    amount = calcExchangeTotalValueForAsset(it.key, it.value),
                    base = USD_UPPER
                )
            }
        }

        val totalNavsForEntity = getNavEventsForEntity(entity).count().toLong()
        return PagedResults(
            pages = totalNavsForEntity.pageCountOfResults(count),
            results = entityAssets,
            total = totalNavsForEntity
        )
    }

    fun LedgerEntityRecord.toEntityLedgeredAsset(): EntityLedgeredAsset {
        val entityValue = totalBalanceByEntity(MetricRangeType.DAY, this)

        return EntityLedgeredAsset(
            id = uuid,
            name = name,
            type = type.displayText,
            amount = entityValue.amount,
            base = USD_UPPER,
            trend = entityValue.trend
        )
    }
}
