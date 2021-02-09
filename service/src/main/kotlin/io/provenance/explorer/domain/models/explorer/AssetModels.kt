package io.provenance.explorer.domain.models.explorer

import io.provenance.explorer.domain.models.clients.CustomPubKey
import io.provenance.explorer.domain.models.clients.DenomAmount
import java.math.BigDecimal
import java.math.BigInteger
import java.security.PublicKey


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
    val percentage: Double
)

data class AccountDetail(
    val accountType: String,
    val address: String,
    val accountNumber: Long,
    val sequence: Int,
    val publicKey: CustomPubKey?,
    val balances: List<DenomAmount>
)


