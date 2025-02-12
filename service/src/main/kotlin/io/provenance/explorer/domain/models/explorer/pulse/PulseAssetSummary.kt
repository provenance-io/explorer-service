package io.provenance.explorer.domain.models.explorer.pulse

import java.math.BigDecimal
import java.util.UUID

data class PulseAssetSummary(
    val id: UUID,
    val name: String,
    val description: String,
    val symbol: String,
    val base: String,
    val quote: String,
    val marketCap: BigDecimal,
    val priceTrend: MetricTrend?,
    val volumeTrend: MetricTrend?,
)
