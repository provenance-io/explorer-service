package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.node.ObjectNode
import java.math.BigDecimal
import java.math.BigInteger


data class AssetListed(
    val marker: String,
    val ownerAddress: String,
    val supply: AssetSupply
)

data class AssetDetail(
    val marker: String,
    val ownerAddress: String,
    val managingAccounts: List<String>,
    val supply: AssetSupply,
    val mintable: Boolean,
    val holderCount: Int,
    val txnCount: BigInteger?,
    val attributes: List<ObjectNode>,
    val metadata: ObjectNode,
    val tokens: TokenCounts
)

data class AssetSupply(
    val circulation: BigInteger,
    val total: BigInteger
)

data class TokenCounts(
    val fungibleCount: Int,
    val nonFungibleCount: Int
)

data class Attribute(
    val createdBy: String,
    val name: String,
    val value: String,
    val valueType: String
)

data class AssetHolder(
    val ownerAddress: String,
    val balance: CountTotal
)

data class AccountDetail(
    val accountType: String,
    val address: String,
    val accountNumber: Long,
    val sequence: Int,
    val publicKeys: Signatures,
    val balances: List<Coin>
)
