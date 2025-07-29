package io.provenance.explorer.domain.models.explorer.pulse

import java.math.BigDecimal

data class EntityLedgeredAsset(
    val id: String,
    val name: String,
    val type: String,
    val amount: BigDecimal,
    val base: String,
    val trend: MetricTrend?
)

data class EntityLedgeredAssetDetail(
    val scopeId: String? = null,
    val denom: String? = null,
    val amount: BigDecimal,
    val base: String,
    val valueOwnerAddress: String? = null,
    val valueOwnerDenom: String? = null
)

data class NftFloorPrice(
    val contract_address: String,
    val name: String,
    val symbol: String,
    val slug: String,
    val floor: BigDecimal,
    val volume: BigDecimal,
    val total_supply: Int,
    val sales_count: Int,
    val unique_owners: String,
    val image_url: String
)
