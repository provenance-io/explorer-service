package io.provenance.explorer.domain.extensions

import io.provenance.explorer.model.base.USD_LOWER

data class ChainScopeNav(
    val priceAmount: Long,
    val priceDenom: String,
    val volume: Long,
    val updatedBlockHeight: Long
)

/**
 * Picks the NAV used for Pulse millidollar totals.
 * Prefers `usd` when a scope reports more than one denom.
 */
fun pickPreferredScopeNav(navs: List<ChainScopeNav>): ChainScopeNav? {
    if (navs.isEmpty()) return null
    navs.firstOrNull { it.priceDenom.equals(USD_LOWER, ignoreCase = true) }?.let { return it }
    return navs.firstOrNull { it.priceDenom.lowercase() in usdPriceDenoms }
}
