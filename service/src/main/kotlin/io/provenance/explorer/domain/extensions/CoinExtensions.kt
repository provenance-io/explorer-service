package io.provenance.explorer.domain.extensions

import cosmos.base.v1beta1.CoinOuterClass
import io.provenance.explorer.domain.models.explorer.CoinStr
import java.math.BigDecimal


const val NHASH = "nhash"

fun BigDecimal.stringfy() = this.stripTrailingZeros().toPlainString()

fun BigDecimal.toCoinStr(denom: String) = CoinStr(this.stringfy(), denom)

// used by protos to translate decimals
fun String.toDecCoin() = BigDecimal(this.toBigInteger(), 18).stripTrailingZeros().toPlainString()

fun List<CoinOuterClass.Coin>.toProtoCoin() =
    this.firstOrNull() ?: CoinOuterClass.Coin.newBuilder().setAmount("0").setDenom("").build()
