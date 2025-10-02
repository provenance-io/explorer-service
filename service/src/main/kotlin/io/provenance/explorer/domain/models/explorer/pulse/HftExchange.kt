package io.provenance.explorer.domain.models.explorer.pulse

import java.math.BigDecimal

data class HftMarket(
    val lastTradedPrice: BigDecimal,
    val volume24h: BigDecimal,
)

data class HftTrades(
    val matches: List<HftMatch>
)

data class HftMatch(
    val id: String,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val created: String
)
