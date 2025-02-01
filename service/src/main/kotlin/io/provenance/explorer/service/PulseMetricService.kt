package io.provenance.explorer.service

import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN_BASE_MULTIPLIER
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.entities.PulseCacheRecord
import io.provenance.explorer.domain.extensions.calculatePulseMetricTrend
import io.provenance.explorer.domain.extensions.roundWhole
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.extensions.toThirdDecimal
import io.provenance.explorer.domain.models.explorer.pulse.HashMetricType
import io.provenance.explorer.domain.models.explorer.pulse.MetricTrend
import io.provenance.explorer.domain.models.explorer.pulse.MetricTrendPeriod
import io.provenance.explorer.domain.models.explorer.pulse.PulseCacheType
import io.provenance.explorer.domain.models.explorer.pulse.PulseMetric
import io.provenance.explorer.model.TokenSupply
import io.provenance.explorer.model.base.USD_UPPER
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Service handler for the Provenance Pulse application
 */
@Service
class PulseMetricService(
    private val tokenService: TokenService,
    private val pricingService: PricingService
) {
    val base = UTILITY_TOKEN
    val quote = USD_UPPER

    /**
     * Builds or retrieves the Pulse Metric cache for the given type and date
     */
    private inline fun <reified T> buildOrRetrieveCache(
        dateTime: LocalDateTime,
        type: PulseCacheType,
        denom: String = UTILITY_TOKEN,
        objFun: () -> T
    ) = PulseCacheRecord.findByDateAndType(dateTime, type).let { cache ->
        if (cache == null) {
            val o = objFun()
            PulseCacheRecord.save(
                dateTime,
                denom,
                type,
                OBJECT_MAPPER.valueToTree(o)
            )
            o
        } else {
            OBJECT_MAPPER.treeToValue(cache.data, T::class.java)
        }
    }

    /**
     * Returns the token supply for the last 2 days to use for Hash-specific
     * metrics
     */
    private inline fun <reified T> retrieve2DaysOfCacheType(
        type: PulseCacheType,
        denom: String = UTILITY_TOKEN,
        crossinline objFun: () -> T
    ): Pair<T, T> = transaction {
        val today = LocalDateTime.now().startOfDay()
        val yesterday = today.minusDays(1)

        Pair(
            buildOrRetrieveCache<T>(
                dateTime = yesterday,
                type = type,
                denom = denom,
                objFun = objFun
            ),
            buildOrRetrieveCache<T>(
                dateTime = today,
                type = type,
                denom = denom,
                objFun = objFun
            )
        )
    }

    private fun change(previous: BigDecimal, current: BigDecimal) =
        current.minus(previous)

    private fun percentageChange(
        previous: BigDecimal,
        current: BigDecimal
    ) = if (current == BigDecimal.ZERO) BigDecimal.ZERO
    else change(previous, current) / previous * BigDecimal(100)

    private fun buildPulseMetric(
        previous: BigDecimal,
        current: BigDecimal,
        base: String = UTILITY_TOKEN,
        quote: String? = USD_UPPER
    ) =
        PulseMetric(
            id = UUID.randomUUID(),
            base = base,
            amount = current,
            quote = quote,
            trend = MetricTrend(
                previousQuantity = previous,
                currentQuantity = current,
                changeQuantity = change(previous, current),
                percentage = percentageChange(previous, current),
                type = current.minus(previous).calculatePulseMetricTrend(),
                period = MetricTrendPeriod.DAY
            )
        )

    /**
     * Returns the hash market cap metric comparing the previous day's market cap
     * to the current day's market cap
     */
    private fun hashMarketCapMetric(): PulseMetric =
        tokenService.getTokenHistorical(
            LocalDate.now().minusDays(1).atStartOfDay(), LocalDateTime.now()
        ).firstOrNull { it.quote[quote] != null }.let {
            if (it == null) throw ResourceNotFoundException("No quote found for $quote")
            val q = it.quote[quote]!!
            val previousMarketCap = q.open.multiply(
                tokenService.totalSupply().divide(UTILITY_TOKEN_BASE_MULTIPLIER)
            ).toThirdDecimal()
            return buildPulseMetric(
                previousMarketCap,
                q.market_cap
            )
        }

    /**
     * Returns the hash metrics for the given type
     */
    fun hashMetric(type: HashMetricType): PulseMetric {
        val (yesterday, current) =
            retrieve2DaysOfCacheType<TokenSupply>(
                type = PulseCacheType.HASH_SUPPLY_METRIC
            ) { tokenService.getTokenBreakdown() }

        return when (type) {
            HashMetricType.STAKED_METRIC ->
                buildPulseMetric(
                    previous = yesterday.bonded.amount.toBigDecimal()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole(),
                    current = current.bonded.amount.toBigDecimal()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole(),
                    quote = null
                )

            HashMetricType.CIRCULATING_METRIC ->
                buildPulseMetric(
                    previous = yesterday.circulation.amount.toBigDecimal()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole(),
                    current = current.circulation.amount.toBigDecimal()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole(),
                    quote = null
                )

            HashMetricType.SUPPLY_METRIC ->
                buildPulseMetric(
                    previous = yesterday.maxSupply.amount.toBigDecimal()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole(),
                    current = current.maxSupply.amount.toBigDecimal()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole(),
                    quote = null
                )

            HashMetricType.MARKET_CAP_METRIC -> hashMarketCapMetric()
        }
    }

    private fun pulseMarketCap(): PulseMetric {
        val (yesterday, current) =
            retrieve2DaysOfCacheType<Map<String, String>>(
                type = PulseCacheType.PULSE_MARKET_CAP_METRIC
            ) { mapOf("market_cap" to pricingService.getTotalAum().toPlainString()) }
        val yBD = yesterday.getValue("market_cap").toBigDecimal()
        val cBD = current.getValue("market_cap").toBigDecimal()
        return buildPulseMetric(yBD, cBD)
    }

    /**
     * Returns the pulse metric for the given type - pulse metrics are "global"
     * metrics that are not specific to Hash
     */
    fun pulseMetric(type: PulseCacheType): PulseMetric {
        return when (type) {
            PulseCacheType.PULSE_MARKET_CAP_METRIC -> pulseMarketCap()
            PulseCacheType.PULSE_TRANSACTION_VOLUME_METRIC -> hashMetric(
                HashMetricType.CIRCULATING_METRIC
            )

            PulseCacheType.PULSE_FEES_AUCTIONS_METRIC -> hashMetric(
                HashMetricType.CIRCULATING_METRIC
            )

            PulseCacheType.PULSE_RECEIVABLES_METRIC -> hashMetric(
                HashMetricType.CIRCULATING_METRIC
            )

            PulseCacheType.PULSE_TRADE_SETTLEMENT_METRIC -> hashMetric(
                HashMetricType.CIRCULATING_METRIC
            )

            PulseCacheType.PULSE_PARTICIPANTS_METRIC -> hashMetric(
                HashMetricType.CIRCULATING_METRIC
            )

            PulseCacheType.PULSE_MARGIN_LOANS_METRIC -> hashMetric(
                HashMetricType.CIRCULATING_METRIC
            )

            PulseCacheType.PULSE_DEMOCRATIZED_PRIME_POOLS_METRIC -> hashMetric(
                HashMetricType.CIRCULATING_METRIC
            )
            else -> throw ResourceNotFoundException("No pulse metric found for $type")
        }
    }
}
