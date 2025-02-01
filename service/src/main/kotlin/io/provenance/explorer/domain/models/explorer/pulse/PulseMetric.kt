package io.provenance.explorer.domain.models.explorer.pulse

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
)
