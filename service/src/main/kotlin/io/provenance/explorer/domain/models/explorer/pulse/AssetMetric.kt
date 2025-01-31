package io.provenance.explorer.domain.models.explorer.pulse

import java.math.BigDecimal
import java.util.UUID

data class AssetMetric(
    val id: UUID,
    val base: String,
    val amount: BigDecimal,
    val quote: String?,
    val trend: MetricTrend?,
)
