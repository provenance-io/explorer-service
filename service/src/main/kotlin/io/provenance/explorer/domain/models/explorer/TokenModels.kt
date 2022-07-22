package io.provenance.explorer.domain.models.explorer

data class TokenSupply(
    val maxSupply: CoinStr,
    val currentSupply: CoinStr,
    val circulation: CoinStr,
    val communityPool: CoinStr,
    val bonded: CoinStr,
    val burned: CoinStr
)

data class TokenDistributionPaginatedResults(
    val ownerAddress: String,
    val data: CountStrTotal
)

data class TokenDistributionAmount(
    val denom: String,
    val amount: String
)

data class TokenDistribution(
    val range: String,
    val amount: TokenDistributionAmount,
    val percent: String
)

data class RichAccount(
    val address: String,
    val amount: CoinStr,
    val percentage: String
)
