package io.provenance.explorer.domain.models.explorer.pulse

import java.math.BigDecimal

data class MetricProgress(
    val percentage: BigDecimal,
    val description: String
)
