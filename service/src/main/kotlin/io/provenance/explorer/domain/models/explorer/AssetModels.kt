package io.provenance.explorer.domain.models.explorer

import com.google.protobuf.Any
import cosmos.base.v1beta1.CoinOuterClass
import java.math.BigDecimal
import java.math.BigInteger


data class AssetListed(
    val marker: String,
    val ownerAddress: String,
    val circulation: BigDecimal,
    val totalSupply: BigDecimal
)

data class AssetDetail(
    val marker: String,
    val ownerAddress: String,
    val managingAccounts: List<String>,
    val circulation: BigDecimal,
    val totalSupply: BigDecimal,
    val mintable: Boolean,
    val holderCount: Int,
    val txnCount: BigInteger?
)

data class AssetHolder(
    val ownerAddress: String,
    val balance: BigDecimal,
    val percentage: BigDecimal
)

data class AccountDetail(
    val accountType: String,
    val address: String,
    val accountNumber: Long,
    val sequence: Int,
    val publicKey: String?,
    val balances: List<Coin>
)

data class Coin ( val amount: BigInteger, val denom: String)

fun CoinOuterClass.Coin.toData() = Coin(this.amount.toBigInteger(), this.denom)
