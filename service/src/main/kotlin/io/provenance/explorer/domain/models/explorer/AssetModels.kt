package io.provenance.explorer.domain.models.explorer

import io.provenance.explorer.model.base.USD_LOWER
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class AssetPricing(
    val id: UUID,
    val markerAddress: String,
    val markerDenom: String,
    val price: BigDecimal?,
    val priceDenomination: String = USD_LOWER,
    val priceTimestamp: OffsetDateTime,
    val usdPrice: BigDecimal?
)
