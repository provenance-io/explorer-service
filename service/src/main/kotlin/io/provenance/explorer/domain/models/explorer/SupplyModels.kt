package io.provenance.explorer.domain.models.explorer

data class TokenSupply(
    val circulation: CoinStr,
    val communityPool: CoinStr,
    val bonded: CoinStr,
)