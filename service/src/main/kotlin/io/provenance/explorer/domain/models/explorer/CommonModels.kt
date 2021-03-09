package io.provenance.explorer.domain.models.explorer

import cosmos.base.v1beta1.CoinOuterClass
import java.math.BigDecimal
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

data class Coin ( val amount: BigInteger, val denom: String)
data class CoinDec (val amount: BigDecimal, val denom: String)

fun CoinOuterClass.Coin.toData() = Coin(this.amount.toBigInteger(), this.denom)

data class CountTotal(
    val count: Int,
    val total: Int
)

data class BondedTokens(
    val count: BigInteger,
    val total: BigInteger?,
    val denom: String
)
