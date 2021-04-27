package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.node.ObjectNode
import java.math.BigInteger


data class AssetListed(
    val marker: String,
    val holdingAccount: String,
    val supply: AssetSupply,
    val status: String
)

data class AssetDetail(
    val marker: String,
    val holdingAccount: String,
    val managingAccounts: AssetManagement,
    val supply: AssetSupply,
    val mintable: Boolean,
    val holderCount: Int,
    val txnCount: BigInteger?,
    val attributes: List<ObjectNode>,
    val metadata: ObjectNode,
    val tokens: TokenCounts,
    val markerStatus: String
)

data class AssetSupply(
    val initial: String,
    val circulation: String
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
    val balance: CountStrTotal
)

data class AccountDetail(
    val accountType: String,
    val address: String,
    val accountNumber: Long,
    val sequence: Int,
    val publicKeys: Signatures,
    val balances: List<CoinStr>
)

data class AssetManagement(
    val managers: Map<String, List<String>>,
    val allowGovControl: Boolean
)
