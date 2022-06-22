package io.provenance.explorer.domain.extensions

import cosmos.base.v1beta1.CoinOuterClass
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.domain.models.explorer.CoinStr
import java.math.BigDecimal
import java.math.RoundingMode

const val NHASH = "nhash"
const val USD_UPPER = "USD"
const val USD_LOWER = "usd"

fun BigDecimal.stringfy() = this.stripTrailingZeros().toPlainString()

fun BigDecimal.toCoinStr(denom: String) = CoinStr(this.stringfy(), denom)

fun String.toDecimalString() = this.toDecimal().toPlainString()

// Used to convert voting power values from mhash (milli) to nhash (nano)
fun Long.mhashToNhash() = this * 1000000

fun List<CoinOuterClass.Coin>.toProtoCoin() =
    this.firstOrNull() ?: CoinOuterClass.Coin.newBuilder().setAmount("0").setDenom("").build()

fun List<CoinOuterClass.Coin>.toCoinStrList() = this.map { CoinStr(it.amount, it.denom) }

fun ServiceOuterClass.GetTxResponse.toCoinStr() =
    this.tx.authInfo.fee.amountList.toProtoCoin().let { coin -> CoinStr(coin.amount, coin.denom) }

// Math extensions
fun String.toPercentage() =
    BigDecimal(this.toBigInteger(), 18).multiply(BigDecimal(100)).stripTrailingZeros().toPlainString() + "%"

fun String.toDecimal() = BigDecimal(this.toBigInteger(), 18).stripTrailingZeros()

fun Double.toPercentage() = "${this * 100}%"

fun List<Int>.avg() = this.sum() / this.size

fun Int.padToDecString() = (this * 1e16).toString()

fun List<CoinOuterClass.DecCoin>.isZero(): Boolean {
    this.forEach {
        if (it.amount.toLong() != 0L)
            return false
    }
    return true
}

fun CoinOuterClass.Coin.diff(minus: CoinOuterClass.Coin) = this.amount.toBigDecimal().minus(minus.amount.toBigDecimal())

// Calc percentage of this based on given numerator/denominator
fun CoinOuterClass.Coin.toPercentage(num: Long, den: Long) =
    this.amount.toBigDecimal()
        .multiply(
            num.toBigDecimal().divide(den.toBigDecimal(), 100, RoundingMode.HALF_EVEN)
        )
        .setScale(0, RoundingMode.HALF_EVEN)
        .toCoinStr(this.denom)

// Calcs the difference between this (oldList) of denoms and the newList of denoms
fun List<CoinStr>.diff(newList: List<CoinStr>) =
    if (this.isEmpty())
        newList
    else
        newList.associateBy { it.denom }
            .let { map ->
                this.map { orig ->
                    CoinStr(map[orig.denom]!!.amount.toBigInteger().minus(orig.amount.toBigInteger()).toString(), orig.denom)
                }
            }
