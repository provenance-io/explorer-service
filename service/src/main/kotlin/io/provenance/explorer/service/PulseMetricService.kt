package io.provenance.explorer.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
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
import io.provenance.explorer.domain.models.explorer.pulse.ExchangeSummary
import io.provenance.explorer.domain.models.explorer.pulse.MetricSeries
import io.provenance.explorer.domain.models.explorer.pulse.PulseAssetSummary
import io.provenance.explorer.domain.models.explorer.pulse.PulseCacheType
import io.provenance.explorer.domain.models.explorer.pulse.PulseMetric
import io.provenance.explorer.domain.models.explorer.pulse.TransactionSummary
import io.provenance.explorer.grpc.v1.ExchangeGrpcClient
import io.provenance.explorer.model.ValidatorState.ACTIVE
import io.provenance.explorer.model.base.PagedResults
import io.provenance.explorer.model.base.USD_LOWER
import io.provenance.explorer.model.base.USD_UPPER
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/**
 * Service handler for the Provenance Pulse application
 */
@Service
class PulseMetricService(
    private val tokenService: TokenService,
    private val validatorService: ValidatorService,
    private val pricingService: PricingService,
    private val assetService: AssetService,
    private val exchangeGrpcClient: ExchangeGrpcClient
) {
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

    val base = UTILITY_TOKEN
    val quote = USD_UPPER

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

    /**
     * Creates a cache record for the given type if it does not exist, or fetches the cache record.
     * Pulse operates on the premise that 2 days of history are required to calculate the current day's metric
     * where daily history is built up from a scheduled task refreshing the metric from a functional data source.
     */
    private fun fetchOrBuildCacheFromDataSource(
        type: PulseCacheType,
        subtype: String? = null,
        dataSourceFn: () -> PulseMetric
    ): PulseMetric = transaction {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        val todayCache = fromPulseMetricCache(today, type, subtype)

        // if this is a scheduled task, bust the cache
        val bustCache = isScheduledTask()

        if (todayCache == null || bustCache) {
            val metric = dataSourceFn()
            val yesterdayMetric =
                fromPulseMetricCache(yesterday, type, subtype)
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
        } else todayCache
    }

    /**
     * Use the assumption that Spring scheduled task threads begin with "scheduling-"
     * to determine if the current thread is a scheduled task
     */
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

        pulseAssetSummaries()

        logger.info("Pulse cache refreshed for thread $threadName")
    }

    private fun denomSupplyCache(denom: String) =
        denomCurrentSupplyCache.get(denom) {
            assetService.getCurrentSupply(denom).toBigDecimal()
        } ?: BigDecimal.ZERO

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

    /**
     * Returns the total committed assets across all exchanges - a simple count of all commitments
     */
    private fun exchangeCommittedAssets(): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_COMMITTED_ASSETS_METRIC
        ) {
            runBlocking {
                exchangeGrpcClient.totalCommitmentCount().toBigDecimal().let {
                    PulseMetric.build(
                        base = UTILITY_TOKEN, // this is just a placeholder denom, not the query
                        amount = it
                    )
                }
            }
        }

    /**
     * Returns the total committed assets value across all exchanges - a sum of all commitments
     */
    private fun exchangeCommittedAssetsValue(): PulseMetric =
        fetchOrBuildCacheFromDataSource(
            type = PulseCacheType.PULSE_COMMITTED_ASSETS_VALUE_METRIC
        ) {
            committedAssetTotals().values.sumOf { it }.let {
                PulseMetric.build(
                    base = USD_UPPER,
                    amount = it.times(inversePowerOfTen(6))
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
            PulseCacheType.PULSE_COMMITTED_ASSETS_METRIC -> exchangeCommittedAssets()
            PulseCacheType.PULSE_COMMITTED_ASSETS_VALUE_METRIC -> exchangeCommittedAssetsValue()
            PulseCacheType.PULSE_DEMOCRATIZED_PRIME_POOLS_METRIC -> todoPulse()
            PulseCacheType.PULSE_MARGIN_LOANS_METRIC -> todoPulse()
            PulseCacheType.PULSE_FEES_AUCTIONS_METRIC -> todoPulse()
            else -> throw ResourceNotFoundException("Invalid pulse metric request for type $type")
        }
    }

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
        source: String? = null
    ) =
        NavEventsRecord.getNavEvents(
            denom = denom,
            fromDate = LocalDateTime.now().startOfDay(),
            source = source
        ).filter {
            (source != null || it.source.startsWith("x/exchange")) &&
                    it.priceDenom?.lowercase()
                        ?.startsWith("u$USD_LOWER") == true
        }

    /**
     * Returns the total committed assets across all exchanges
     */
    private fun committedAssetTotals() = runBlocking {
        exchangeGrpcClient.totalCommittedAssetTotals()
    }

    /**
     * TODO - this is problematic because it assumes all assets are USD-based
     */
    fun pulseAssetSummaries(): List<PulseAssetSummary> =
        committedAssetTotals().keys.distinct().map { denom ->
            val denomMetadata = pulseAssetDenomMetadata(denom)
            val denomExp = denomExponent(denomMetadata) ?: 1
            val denomPow = inversePowerOfTen(denomExp)
            val usdPow = inversePowerOfTen(6)
            val priceMetric = fetchOrBuildCacheFromDataSource(
                type = PulseCacheType.PULSE_ASSET_PRICE_SUMMARY_METRIC,
                subtype = denom
            ) {
                val events = pulseAssetSummariesForNavEvents(denom)
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
                subtype = denom
            ) {
                val events = pulseAssetSummariesForNavEvents(denom)
                val tradeValue = events.sumOf { it.priceAmount!! }
                    .toBigDecimal()
                    .times(usdPow)

                PulseMetric.build(
                    base = USD_UPPER,
                    amount = tradeValue
                )
            }
            val supply = denomSupplyCache(denom)
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
            LocalDateTime.now().minusDays(1).startOfDay(),
            page,
            count,
            sort,
            sortColumn
        ).let { pr ->
            val denomMetadata = pulseAssetDenomMetadata(denom)
            val denomExp = denomExponent(denomMetadata) ?: 1
            val denomPow = inversePowerOfTen(denomExp)
            val denomPrice =
                fromPulseMetricCache(
                    LocalDateTime.now().minusDays(1).startOfDay().toLocalDate(),
                    PulseCacheType.PULSE_ASSET_PRICE_SUMMARY_METRIC, denom
                )?.amount ?: BigDecimal.ZERO

            PagedResults(
                pages = pr.pages,
                results = pr.results.map { tx ->
                    val denomTotal =
                        if (tx["denom_total"] != null)
                            BigDecimal(tx["denom_total"].toString()).times(denomPow)
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
}
