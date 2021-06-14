package io.provenance.explorer.domain.models.explorer

import cosmos.base.v1beta1.CoinOuterClass
import java.math.BigInteger


data class PagedResults<T>(val pages: Int, val results: List<T>)

data class Addresses(
    val baseHash : String,
    val accountAddr: String,
    val validatorAccountAddr: String,
    val consensusAccountAddr: String,
)

data class Signatures(
    val signers: List<String>,
    val threshold: Int?
)

data class CoinStr ( val amount: String, val denom: String)

fun CoinOuterClass.Coin.toData() = CoinStr(this.amount, this.denom)

data class CountTotal(
    val count: BigInteger,
    val total: BigInteger
)

data class CountStrTotal(
    val count: String,
    val total: String?,
    val denom: String
)
