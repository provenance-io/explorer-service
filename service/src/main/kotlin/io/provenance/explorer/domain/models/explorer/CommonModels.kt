package io.provenance.explorer.domain.models.explorer

import cosmos.base.v1beta1.CoinOuterClass
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toDecimalStringOld
import io.provenance.explorer.model.base.CoinStr
import io.provenance.explorer.model.base.CoinStrWithPrice
import io.provenance.explorer.model.base.USD_UPPER
import java.math.BigDecimal

data class Addresses(
    val baseHash: String,
    val accountAddr: String,
    val validatorAccountAddr: String,
    val consensusAccountAddr: String
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

fun CoinOuterClass.DecCoin.toCoinStrWithPrice(price: BigDecimal?) =
    this.amount.toDecimalStringOld().toBigDecimal().toCoinStrWithPrice(price, this.denom)

const val hourlyBlockCount = 720
