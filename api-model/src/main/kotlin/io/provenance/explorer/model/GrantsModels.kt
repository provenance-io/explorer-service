package io.provenance.explorer.model

import io.provenance.explorer.model.base.CoinStr
import java.time.LocalDateTime

//region Authz Grant
data class GrantData(
    val granter: String,
    val grantee: String,
    val expiration: LocalDateTime,
    val type: String,
    val authorization: AuthzGrant?
)

open class AuthzGrant

data class GenericAuth(
    val msg: String
) : AuthzGrant()

data class CountAuth(
    val msg: String,
    val allowedAuthCount: Int
) : AuthzGrant()

data class SendAuth(
    val spendLimits: List<CoinStr>
) : AuthzGrant()

data class StakeAuth(
    val authorizationType: String,
    val allowList: List<String>? = null,
    val denyList: List<String>? = null,
    val maxTokens: CoinStr? = null
) : AuthzGrant()

data class MarkerTransferAuth(
    val transferLimits: List<CoinStr>
) : AuthzGrant()

//endregion

//region Feegrant
data class FeegrantData(
    val granter: String,
    val grantee: String,
    val type: String,
    val allowance: Allowance?
)

open class Allowance

data class BasicAllowance(
    val spendLimits: List<CoinStr>,
    val expiration: LocalDateTime? = null
) : Allowance()

data class PeriodicAllowance(
    val basicAllowance: BasicAllowance,
    // duration in seconds
    val period: String,
    val spendLimit: List<CoinStr>,
    val spendRemaining: List<CoinStr>,
    val periodReset: LocalDateTime
) : Allowance()

data class AllowedMsgAllowance(
    val allowance: Allowance,
    val allowedMsgs: List<String>
) : Allowance()

//endregion
