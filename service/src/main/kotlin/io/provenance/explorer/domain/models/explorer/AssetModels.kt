package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.node.ObjectNode
import io.provenance.explorer.domain.extensions.USD_LOWER
import org.joda.time.DateTime
import java.math.BigDecimal
import java.math.BigInteger
import java.time.OffsetDateTime
import java.util.UUID

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
    val publicKey: AccountSigInfo,
    val accountName: String?,
    val attributes: List<AttributeObj>,
    val tokens: TokenCounts,
    val isContract: Boolean,
    val accountAum: CoinStr,
    val isVesting: Boolean
)

data class AccountVestingInfo(
    val dataAsOfDate: DateTime,
    val endTime: DateTime,
    val originalVestingList: List<CoinStr>,
    val startTime: DateTime? = null,
    val periodicVestingList: List<PeriodicVestingInfo> = emptyList(),
)

data class PeriodicVestingInfo(
    val length: Long, // in seconds
    val coins: List<CoinStr>,
    val vestingDate: DateTime,
    val isVested: Boolean
)

data class AssetManagement(
    val managers: Map<String, List<String>>,
    val allowGovControl: Boolean
)

data class AccountRewards(
    val rewards: List<Reward>,
    val total: List<CoinStrWithPrice>
)

data class Reward(
    val validatorAddress: String,
    val reward: List<CoinStrWithPrice>
)

data class AttributeObj(
    val attribute: String,
    val data: String
)

data class AssetPricing(
    val id: UUID,
    val markerAddress: String,
    val markerDenom: String,
    val price: BigDecimal?,
    val priceDenomination: String = USD_LOWER,
    val priceTimestamp: OffsetDateTime,
    val usdPrice: BigDecimal?
)

data class DenomBalanceBreakdown(
    val total: CoinStrWithPrice,
    val spendable: CoinStrWithPrice,
    val locked: CoinStrWithPrice
)
