package io.provenance.explorer.domain.models.explorer.pulse

import io.provenance.explorer.domain.extensions.calculatePulseMetricTrend
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class PulseMetric(
    val id: UUID,
    val base: String,
    val amount: BigDecimal,
    val quote: String? = null,
    val quoteAmount: BigDecimal?,
    val trend: MetricTrend? = null,
    val progress: MetricProgress? = null,
    val series: MetricSeries? = null
) {
    companion object {
        fun build(
            amount: BigDecimal,
            base: String,
            quote: String? = null,
            quoteAmount: BigDecimal? = null,
            series: MetricSeries? = null
        ) = PulseMetric(
            id = UUID.randomUUID(),
            base = base,
            amount = amount,
            quote = quote,
            quoteAmount = quoteAmount,
            series = series
        )

        fun build(
            previous: BigDecimal,
            current: BigDecimal,
            base: String,
            quote: String? = null,
            quoteAmount: BigDecimal? = null,
            series: MetricSeries? = null
        ) =
            PulseMetric(
                id = UUID.randomUUID(),
                base = base,
                amount = current,
                quote = quote,
                quoteAmount = quoteAmount,
                trend = MetricTrend(
                    previousQuantity = previous,
                    currentQuantity = current,
                    changeQuantity = change(previous, current),
                    percentage = percentageChange(previous, current),
                    type = current.minus(previous).calculatePulseMetricTrend(),
                    period = MetricTrendPeriod.DAY
                ),
                series = series
            )

        // create a function called build
        private fun change(previous: BigDecimal, current: BigDecimal) =
            current.minus(previous)

        private fun percentageChange(
            previous: BigDecimal,
            current: BigDecimal
        ) = if (previous.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO
        else change(previous, current).divide(previous, 8, RoundingMode.HALF_UP) * BigDecimal(100)
    }
}
