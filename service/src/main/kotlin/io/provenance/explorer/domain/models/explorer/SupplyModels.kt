package io.provenance.explorer.domain.models.explorer

data class TokenSupply(
    val circulation: String,
    val communityPool: String,
    val bonded: String,
)