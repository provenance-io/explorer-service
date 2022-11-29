package io.provenance.explorer.domain.models.explorer

import cosmos.base.v1beta1.CoinOuterClass
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.extensions.USD_UPPER
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toDecimalStringOld
import java.math.BigDecimal
import java.math.BigInteger

data class PagedResults<T>(
    val pages: Int,
    val results: List<T>,
    val total: Long,
    val rollupTotals: Map<String, CoinStr> = emptyMap()
)

data class Addresses(
    val baseHash: String,
    val accountAddr: String,
    val validatorAccountAddr: String,
    val consensusAccountAddr: String
)

data class CoinStr(val amount: String, val denom: String)
data class CoinStrWithPrice(
    val amount: String,
    val denom: String,
    val pricePerToken: CoinStr?,
    val totalBalancePrice: CoinStr?
)

fun CoinOuterClass.Coin.toCoinStr() = CoinStr(this.amount, this.denom)

fun BigDecimal.toCoinStrWithPrice(price: BigDecimal?, denom: String) =
    CoinStrWithPrice(
        this.toBigInteger().toString(),
        denom,
        price?.toCoinStr(USD_UPPER),
        price?.multiply(this)?.toCoinStr(USD_UPPER)
    )

fun CoinOuterClass.Coin.toCoinStrWithPrice(price: BigDecimal?) =
    this.amount.toBigDecimal().toCoinStrWithPrice(price, this.denom)

fun MarkerCacheRecord.toCoinStrWithPrice(price: BigDecimal?) =
    this.supply.toCoinStrWithPrice(price, this.denom)

fun CoinOuterClass.DecCoin.toCoinStrWithPrice(price: BigDecimal?) =
    this.amount.toDecimalStringOld().toBigDecimal().toCoinStrWithPrice(price, this.denom)

data class CountTotal(
    val count: BigInteger?,
    val total: BigInteger
)

data class CountStrTotal(
    val count: String,
    val total: String?,
    val denom: String
)

enum class Timeframe { WEEK, DAY, HOUR, FOREVER }

const val hourlyBlockCount = 720

enum class PeriodInSeconds(val seconds: Int) {
    SECOND(1),
    MINUTE(60),
    HOUR(3600),
    DAY(86400),
    WEEK(604800),
    MONTH(2628000),
    QUARTER(7884000),
    YEAR(31536000)
}
