package io.provenance.explorer.domain.models.clients.pb

import io.provenance.explorer.domain.models.clients.CustomPubKey
import io.provenance.explorer.domain.models.clients.DenomAmount
import io.provenance.explorer.domain.models.clients.Pagination
import io.provenance.explorer.domain.models.clients.PubKey

data class PbStakingPaged(
    val validators: List<PbStakingValidator>,
    val pagination: Pagination
)

data class PbStakingSingle(val validator: PbStakingValidator)

data class PbStakingValidator(
    val operatorAddress: String,
    val consensusPubkey: CustomPubKey,
    val jailed: Boolean,
    val status: String,
    val tokens: String,
    val delegatorShares: String,
    val description: ValidatorDescription,
    val unbondingHeight: String?,
    val unbondingTime: String,
    val commission: Commission,
    val minSelfDelegation: String
)

data class PbDelegations(val delegations: List<PbDelegationResponse>)

data class PbDelegationsPaged(
    val delegationResponses: List<PbDelegationResponse>,
    val pagination: Pagination
)

data class PbDelegationResponse(
    val delegation: PbDelegation,
    val balance: DenomAmount
)

data class PbDelegation(
    val delegatorAddress: String?,
    val validatorAddress: String?,
    val shares: String
)

data class ValidatorDescription(
    val moniker: String,
    val identity: String,
    val website: String,
    val securityContact: String,
    val details: String
)



