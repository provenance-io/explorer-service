package io.provenance.explorer.domain.models.explorer

import cosmos.base.v1beta1.CoinOuterClass
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.extensions.USD_UPPER
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toDecCoin
import java.math.BigDecimal
import java.math.BigInteger

data class PagedResults<T>(
    val pages: Int,
    val results: List<T>,
    val total: Long,
    val rollupTotals: Map<String, CoinStr> = mapOf()
)

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

data class AccountSignature(
    val pubKey: String?,
    val type: String?
)

data class CoinStr(val amount: String, val denom: String)
data class CoinStrWithPrice(
    val amount: String,
    val denom: String,
    val pricePerToken: CoinStr?,
    val totalBalancePrice: CoinStr?
)

fun CoinOuterClass.Coin.toData() = CoinStr(this.amount, this.denom)

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
    this.amount.toDecCoin().toBigDecimal().toCoinStrWithPrice(price, this.denom)

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

enum class Timeframe { WEEK, DAY, HOUR, FOREVER }

const val hourlyBlockCount = 720
