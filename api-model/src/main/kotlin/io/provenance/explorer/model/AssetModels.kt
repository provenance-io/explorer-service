package io.provenance.explorer.model

import com.fasterxml.jackson.databind.node.ObjectNode
import io.provenance.explorer.model.base.CoinStr
import io.provenance.explorer.model.base.CoinStrWithPrice
import io.provenance.explorer.model.base.CountStrTotal
import java.math.BigInteger

data class AssetListed(
    val marker: String,
    val holdingAccount: String?,
    val supply: CoinStrWithPrice,
    val status: String,
    val mintable: Boolean = false,
    val lastTxTimestamp: String?,
    val markerType: String
)

data class AssetDetail(
    val marker: String,
    val holdingAccount: String?,
    val managingAccounts: AssetManagement?,
    val supply: CoinStrWithPrice,
    val mintable: Boolean = false,
    val holderCount: Int,
    val txnCount: BigInteger?,
    val attributes: List<AttributeObj>,
    val metadata: ObjectNode,
    val tokens: TokenCounts,
    val markerStatus: String,
    val markerType: String
)

data class AssetManagement(
    val managers: Map<String, List<String>>,
    val allowGovControl: Boolean
)

data class AssetHolder(
    val ownerAddress: String,
    val balance: CountStrTotal,
    val spendableBalance: CoinStr
)
