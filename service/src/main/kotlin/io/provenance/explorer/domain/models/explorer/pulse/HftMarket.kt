package io.provenance.explorer.domain.models.explorer.pulse

import java.math.BigDecimal

data class HftMarket(
    val lastTradedPrice: BigDecimal,
    val volume24h: BigDecimal,
)
