package io.provenance.explorer.domain.extensions

import cosmos.base.v1beta1.CoinOuterClass
import cosmos.base.v1beta1.coin
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN_BASE_DECIMAL_PLACES
import io.provenance.explorer.config.ExplorerProperties.Companion.VOTING_POWER_PADDING
import io.provenance.explorer.domain.models.explorer.CoinStr
import java.math.BigDecimal
import java.math.RoundingMode

const val USD_UPPER = "USD"
const val USD_LOWER = "usd"

fun BigDecimal.stringfy() = this.stripTrailingZeros().toPlainString()
fun BigDecimal.stringfyWithScale(scale: Int) = this.stripTrailingZeros().setScale(scale, RoundingMode.HALF_EVEN).toPlainString()

fun BigDecimal.toCoinStr(denom: String) = CoinStr(this.stringfy(), denom)

fun String.toDecimalStringOld() = this.toDecimal().toPlainString()

fun String.toDecimalString() = BigDecimal(this).stringfy()

// Used to convert voting power values from mhash (milli) to nhash (nano)
fun BigDecimal.mhashToNhash() = this.multiply(BigDecimal(VOTING_POWER_PADDING))

fun BigDecimal.toProtoCoin(denom: String) = coin {
    this.amount = this@toProtoCoin.toString()
    this.denom = denom
}

fun List<CoinOuterClass.Coin>.toCoinStrList() = this.map { CoinStr(it.amount, it.denom) }

// Math extensions
fun String.toPercentageOld() =
    BigDecimal(this.toBigInteger(), UTILITY_TOKEN_BASE_DECIMAL_PLACES * 2)
        .multiply(BigDecimal(100))
        .stringfyWithScale(4) + "%"

fun String.toPercentage() = BigDecimal(this).multiply(BigDecimal(100)).stringfyWithScale(4) + "%"

fun String.toDecimal() =
    BigDecimal(this.toBigInteger(), UTILITY_TOKEN_BASE_DECIMAL_PLACES * 2).stripTrailingZeros()

fun Double.toPercentage() = "${this * 100}%"
// fun BigDecimal.toPercentage() = "${this * 100}%"

fun List<Int>.avg() = this.sum() / this.size

fun BigDecimal.percentChange(orig: BigDecimal) =
    ((this.minus(orig)).divide(orig, orig.scale(), RoundingMode.HALF_EVEN)).multiply(BigDecimal(100))

fun Int.padToDecString() =
    BigDecimal(this).multiply(BigDecimal("1e${(UTILITY_TOKEN_BASE_DECIMAL_PLACES - 1) * 2}")).toPlainString()

fun List<CoinOuterClass.DecCoin>.isZero(): Boolean {
    this.forEach {
        if (it.amount.toBigDecimal() != BigDecimal.ZERO)
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

fun String.toPercentage(num: BigDecimal, den: BigDecimal, scale: Int) =
    this.toBigDecimal()
        .divide(den, 100, RoundingMode.HALF_EVEN)
        .multiply(num)
        .stringfyWithScale(scale) + "%"

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

fun BigDecimal.roundWhole() = this.setScale(0, RoundingMode.HALF_EVEN)
