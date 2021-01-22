package io.provenance.explorer.domain.models.clients.pb

import io.provenance.explorer.domain.models.clients.DenomAmount
import io.provenance.explorer.domain.models.clients.PubKey

data class PbStakingValidator(
    val operatorAddress: String,
    val consensusPubkey: PubKey,
    val jailed: Boolean,
    val status: Int,
    val tokens: String,
    val delegatorShares: String,
    val description: ValidatorDescription,
    val bondHeight: String?,
    val bondIntraTxCounter: String?,
    val unbondingHeight: String?,
    val unbondingTime: String,
    val commission: Commission,
    val minSelfDelegation: String
)

data class PbDelegations(val delegations: List<PbDelegation>)


data class PbDelegation(
    val delegatorAddress: String?,
    val validatorAddress: String,
    val shares: String,
    val balance: DenomAmount
)

data class ValidatorDescription(
    val moniker: String,
    val identity: String?,
    val website: String?,
    val securityContact: String?,
    val details: String?
)

