package io.provenance.explorer.model.base

import java.math.BigDecimal

data class CoinStr(val amount: String, val denom: String, val spendable: String? = null)

data class CoinStrWithPrice(
    val amount: String,
    val denom: String,
    val pricePerToken: CoinStr?,
    val totalBalancePrice: CoinStr?
)

data class CountStrTotal(
    val count: String,
    val total: String?,
    val denom: String,
    val spendable: String? = null,
)


fun BigDecimal.stringfy() = this.stripTrailingZeros().toPlainString()
