package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.node.ObjectNode
import cosmos.base.v1beta1.CoinOuterClass
import org.joda.time.DateTime
import java.math.BigInteger


data class AssetListed(
    val marker: String,
    val holdingAccount: String?,
    val supply: String,
    val status: String,
    val mintable: Boolean = false,
    val lastTxTimestamp: String?
)

data class AssetDetail(
    val marker: String,
    val holdingAccount: String?,
    val managingAccounts: AssetManagement?,
    val supply: String,
    val mintable: Boolean = false,
    val holderCount: Int,
    val txnCount: BigInteger?,
    val attributes: List<ObjectNode>,
    val metadata: ObjectNode,
    val tokens: TokenCounts,
    val markerStatus: String,
    val markerType: String
)

data class TokenCounts(
    val fungibleCount: Long,
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
    val accountNumber: Long?,
    val sequence: Int?,
    val publicKeys: Signatures,
    val accountName: String?
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
