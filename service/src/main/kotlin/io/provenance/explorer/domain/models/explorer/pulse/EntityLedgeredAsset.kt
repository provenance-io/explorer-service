package io.provenance.explorer.domain.models.explorer.pulse

import java.math.BigDecimal

data class EntityLedgeredAsset(
    val name: String,
    val type: String,
    val amount: BigDecimal,
    val base: String,
    val trend: MetricTrend?
)
