package io.provenance.explorer.domain.models.explorer.pulse

import java.math.BigDecimal

data class MetricTrend(
    val previousQuantity: BigDecimal,
    val currentQuantity: BigDecimal,
    val changeQuantity: BigDecimal,
    val percentage: BigDecimal,
    val type: MetricTrendType,
    val period: MetricTrendPeriod,
)
