package io.provenance.explorer.domain.models.explorer

import cosmos.base.v1beta1.CoinOuterClass
import java.math.BigInteger

data class PagedResults<T>(val pages: Int, val results: List<T>, val total: Long)

data class Addresses(
    val baseHash: String,
    val accountAddr: String,
    val validatorAccountAddr: String,
    val consensusAccountAddr: String
)

data class Signatures(
    val signers: List<String>,
    val threshold: Int?
)

data class CoinStr(val amount: String, val denom: String)

fun CoinOuterClass.Coin.toData() = CoinStr(this.amount, this.denom)

data class CountTotal(
    val count: BigInteger?,
    val total: BigInteger
)

data class CountStrTotal(
    val count: String,
    val total: String?,
    val denom: String
)

data class TokenSupply(
    val currentSupply: CoinStr,
    val circulation: CoinStr,
    val communityPool: CoinStr,
    val bonded: CoinStr
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
