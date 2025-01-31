package io.provenance.explorer.model

import io.provenance.explorer.model.base.CoinStr
import io.provenance.explorer.model.base.CoinStrWithPrice
import java.time.LocalDateTime

data class AccountDetail(
    val accountType: String,
    val address: String,
    val accountNumber: Long?,
    val sequence: Int?,
    val publicKey: AccountSigInfo,
    val accountName: String?,
    val attributes: List<AttributeObj>,
    val tokens: TokenCounts,
    @Deprecated("Use this.flags.isContract instead") val isContract: Boolean,
    val accountAum: CoinStr,
    // added to eventually remove the standalone vals in this object
    @Deprecated("Use this.flags.isVesting instead") val isVesting: Boolean,
    val flags: AccountFlags,
    val accountOwner: String?
)

data class AttributeObj(
    val attribute: String,
    val data: String
)

data class TokenCounts(
    val fungibleCount: Int,
    val nonFungibleCount: Int
)

data class AccountFlags(
    val isContract: Boolean,
    val isVesting: Boolean,
    val isIca: Boolean
)

data class AccountSigInfo(
    val type: String?,
    val base64: String?,
    val sigList: List<AccountSignature>
)

data class AccountSignature(
    val idx: Int,
    val address: String
)

data class AccountRewards(
    val rewards: List<Reward>,
    val total: List<CoinStrWithPrice>
)

data class Reward(
    val validatorAddress: String,
    val reward: List<CoinStrWithPrice>
)

data class DenomBalanceBreakdown(
    val total: CoinStrWithPrice,
    val spendable: CoinStrWithPrice,
    val locked: CoinStrWithPrice
)

data class AccountVestingInfo(
    val dataAsOfDate: LocalDateTime,
    val endTime: LocalDateTime,
    val originalVestingList: List<CoinStr>,
    val startTime: LocalDateTime? = null,
    val periodicVestingList: List<PeriodicVestingInfo> = emptyList()
)

data class PeriodicVestingInfo(
    // in seconds
    val length: Long,
    val coins: List<CoinStr>,
    val vestingDate: LocalDateTime,
    val isVested: Boolean
)
