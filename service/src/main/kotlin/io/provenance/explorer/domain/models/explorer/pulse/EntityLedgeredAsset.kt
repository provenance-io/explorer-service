package io.provenance.explorer.domain.models.explorer.pulse

import java.math.BigDecimal

data class EntityLedgeredAsset(
    val id: String,
    val address: String,
    val name: String,
    val type: String,
    val amount: BigDecimal,
    val base: String,
    val trend: MetricTrend?
)

data class EntityLedgeredAssetDetail(
    val scopeId: String? = null,
    val marker: String? = null,
    val amount: BigDecimal,
    val base: String,
    val valueOwnerAddress: String? = null,
    val valueOwnerDenom: String? = null
)
