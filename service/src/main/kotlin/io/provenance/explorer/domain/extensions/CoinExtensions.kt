package io.provenance.explorer.domain.extensions

import cosmos.base.v1beta1.CoinOuterClass
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN_BASE_DECIMAL_PLACES
import io.provenance.explorer.config.ExplorerProperties.Companion.VOTING_POWER_PADDING
import io.provenance.explorer.domain.models.explorer.CoinStr
import java.math.BigDecimal
import java.math.RoundingMode

const val USD_UPPER = "USD"
const val USD_LOWER = "usd"

fun BigDecimal.stringfy() = this.stripTrailingZeros().toPlainString()

fun BigDecimal.toCoinStr(denom: String) = CoinStr(this.stringfy(), denom)

fun String.toDecimalString() = this.toDecimal().toPlainString()

// Used to convert voting power values from mhash (milli) to nhash (nano)
fun BigDecimal.mhashToNhash() = this * BigDecimal(VOTING_POWER_PADDING)

fun List<CoinOuterClass.Coin>.toProtoCoin() =
    this.firstOrNull() ?: CoinOuterClass.Coin.newBuilder().setAmount("0").setDenom("").build()

fun List<CoinOuterClass.Coin>.toCoinStrList() = this.map { CoinStr(it.amount, it.denom) }

fun ServiceOuterClass.GetTxResponse.toCoinStr() =
    this.tx.authInfo.fee.amountList.toProtoCoin().let { coin -> CoinStr(coin.amount, coin.denom) }

// Math extensions
fun String.toPercentage() =
    BigDecimal(this.toBigInteger(), UTILITY_TOKEN_BASE_DECIMAL_PLACES * 2)
        .multiply(BigDecimal(100))
        .stringify() + "%"

fun String.toDecimal() =
    BigDecimal(this.toBigInteger(), UTILITY_TOKEN_BASE_DECIMAL_PLACES * 2).stripTrailingZeros()

fun Double.toPercentage() = "${this * 100}%"
// fun BigDecimal.toPercentage() = "${this * 100}%"

fun List<Int>.avg() = this.sum() / this.size

fun Int.padToDecString() = (this * 1e16).toString()

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
        .setScale(scale, RoundingMode.HALF_EVEN)
        .stringify() + "%"

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
