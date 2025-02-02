package io.provenance.explorer.domain.models.explorer.pulse

import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.domain.extensions.calculatePulseMetricTrend
import io.provenance.explorer.model.base.USD_UPPER
import java.math.BigDecimal
import java.util.UUID

data class PulseMetric(
    val id: UUID,
    val base: String,
    val amount: BigDecimal,
    val quote: String? = null,
    val trend: MetricTrend? = null,
    val progress: MetricProgress? = null,
    val series: MetricSeries? = null
) {
    companion object {
        fun build(
            amount: BigDecimal,
            base: String = UTILITY_TOKEN,
            quote: String? = USD_UPPER
        ) = PulseMetric(
            id = UUID.randomUUID(),
            base = base,
            amount = amount,
            quote = quote
        )

        fun build(
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

        // create a function called build
        private fun change(previous: BigDecimal, current: BigDecimal) =
            current.minus(previous)

        private fun percentageChange(
            previous: BigDecimal,
            current: BigDecimal
        ) = if (current == BigDecimal.ZERO) BigDecimal.ZERO
        else change(previous, current) / previous * BigDecimal(100)
    }
}
