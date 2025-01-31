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
import io.provenance.explorer.domain.models.explorer.pulse.AssetMetric
import io.provenance.explorer.domain.models.explorer.pulse.HashMetricType
import io.provenance.explorer.domain.models.explorer.pulse.MetricTrend
import io.provenance.explorer.domain.models.explorer.pulse.MetricTrendPeriod
import io.provenance.explorer.domain.models.explorer.pulse.PulseCacheType
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
class PulseService(private val tokenService: TokenService) {
    val base = UTILITY_TOKEN
    val quote = USD_UPPER

    private inline fun <reified T> buildOrRetrieveCache(
        dateTime: LocalDateTime,
        type: PulseCacheType
    ) =
        PulseCacheRecord.findByDateAndType(dateTime, type).let { cache ->
            when (type) {
                PulseCacheType.HASH_SUPPLY_METRIC ->
                    if (cache == null) {
                        val tokenSupply = tokenService.getTokenBreakdown()
                        PulseCacheRecord.save(
                            dateTime,
                            UTILITY_TOKEN,
                            type,
                            OBJECT_MAPPER.valueToTree(tokenSupply)
                        )
                        tokenSupply as T
                    } else {
                        OBJECT_MAPPER.treeToValue(cache.data, T::class.java)
                    }
            }
        }

    private fun hashTokenSupply(): Pair<TokenSupply, TokenSupply> =
        transaction {
            val today = LocalDateTime.now().startOfDay()
            val yesterday = today.minusDays(1)

            Pair(
                buildOrRetrieveCache<TokenSupply>(
                    yesterday,
                    PulseCacheType.HASH_SUPPLY_METRIC
                ),
                buildOrRetrieveCache<TokenSupply>(
                    today,
                    PulseCacheType.HASH_SUPPLY_METRIC
                )
            )
        }

    private fun change(previous: BigDecimal, current: BigDecimal): BigDecimal {
        return current - previous
    }

    private fun percentageChange(
        previous: BigDecimal,
        current: BigDecimal
    ): BigDecimal {
        return if (current == BigDecimal.ZERO) BigDecimal.ZERO
        else change(previous, current) / previous * BigDecimal(100)
    }

    private fun buildHashMetric(
        previous: BigDecimal,
        current: BigDecimal,
        quote: String? = USD_UPPER
    ) =
        AssetMetric(
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

    private fun hashMarketCapMetric(): AssetMetric =
        tokenService.getTokenHistorical(
            LocalDate.now().minusDays(1).atStartOfDay(), LocalDateTime.now()
        ).firstOrNull { it.quote[quote] != null }.let {
            if (it == null) throw ResourceNotFoundException("No quote found for $quote")
            val q = it.quote[quote]!!
            val previousMarketCap = q.open.multiply(
                tokenService.totalSupply().divide(UTILITY_TOKEN_BASE_MULTIPLIER)
            ).toThirdDecimal()
            return buildHashMetric(
                previousMarketCap,
                q.market_cap
            )
        }

    fun hashMetric(type: HashMetricType): AssetMetric {
        val (yesterdaysBreakdown, currentBreakdown) = hashTokenSupply()

        return when (type) {
            HashMetricType.STAKED_METRIC ->
                buildHashMetric(
                    yesterdaysBreakdown.bonded.amount.toBigDecimal()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole(),
                    currentBreakdown.bonded.amount.toBigDecimal()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole(),
                    null
                )

            HashMetricType.CIRCULATING_METRIC ->
                buildHashMetric(
                    yesterdaysBreakdown.circulation.amount.toBigDecimal()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole(),
                    currentBreakdown.circulation.amount.toBigDecimal()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole(),
                    null
                )

            HashMetricType.SUPPLY_METRIC ->
                buildHashMetric(
                    yesterdaysBreakdown.maxSupply.amount.toBigDecimal()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole(),
                    currentBreakdown.maxSupply.amount.toBigDecimal()
                        .divide(UTILITY_TOKEN_BASE_MULTIPLIER).roundWhole(),
                    null
                )

            HashMetricType.MARKET_CAP_METRIC -> hashMarketCapMetric()
        }
    }
}
