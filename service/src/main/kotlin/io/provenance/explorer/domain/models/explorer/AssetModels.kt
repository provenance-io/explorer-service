package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.node.ObjectNode
import java.math.BigInteger

data class AssetListed(
    val marker: String,
    val holdingAccount: String?,
    val supply: CoinStr,
    val status: String,
    val mintable: Boolean = false,
    val lastTxTimestamp: String?,
    val markerType: String
)

data class AssetDetail(
    val marker: String,
    val holdingAccount: String?,
    val managingAccounts: AssetManagement?,
    val supply: CoinStr,
    val mintable: Boolean = false,
    val holderCount: Int,
    val txnCount: BigInteger?,
    val attributes: List<AttributeObj>,
    val metadata: ObjectNode,
    val tokens: TokenCounts,
    val markerStatus: String,
    val markerType: String
)

data class TokenCounts(
    val fungibleCount: Long,
    val nonFungibleCount: Int
)

data class AssetHolder(
    val ownerAddress: String,
    val balance: CountStrTotal
)

data class AccountDetail(
    val accountType: String,
    val address: String,
    val accountNumber: Long?,
    val sequence: Int?,
    val publicKeys: Signatures,
    val accountName: String?,
    val attributes: List<AttributeObj>,
    val tokens: TokenCounts
)

data class AssetManagement(
    val managers: Map<String, List<String>>,
    val allowGovControl: Boolean
)

data class AccountRewards(
    val rewards: List<Reward>,
    val total: List<CoinStr>
)

data class Reward(
    val validatorAddress: String,
    val reward: List<CoinStr>
)

data class AttributeObj(
    val attribute: String,
    val data: String
)
