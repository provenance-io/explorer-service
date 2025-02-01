package io.provenance.explorer.domain.models.explorer.pulse

import java.math.BigDecimal

data class MetricSeries(
    val seriesData: List<BigDecimal>,
    val label: String
)
