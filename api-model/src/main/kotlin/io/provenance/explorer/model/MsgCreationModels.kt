package io.provenance.explorer.model

import cosmos.gov.v1.Gov
import cosmwasm.wasm.v1.Types
import io.provenance.explorer.model.base.CoinStr

//region Governance Msg Models
enum class ProposalType(val example: BaseProposal) {
    TEXT(BaseProposal()),
    PARAMETER_CHANGE(
        ParameterChangeData(
            listOf(
                ParamChangeObj("param_space", "param_key", "param_value"),
                ParamChangeObj("attribute", "not_a_real_key", "blah")
            )
        )
    ),
    SOFTWARE_UPGRADE(SoftwareUpgradeData("Test Name", 1000087, "This is info for the upgrade.")),
    CANCEL_UPGRADE(BaseProposal()),
    STORE_CODE(
        StoreCodeData(
            "run as address",
            StoreCodeAccessConfig(
                Types.AccessType.ACCESS_TYPE_EVERYBODY
            )
        )
    ),
    INSTANTIATE_CONTRACT(
        InstantiateContractData(
            "run as address",
            "admin address or null",
            140,
            "Unique label for easy identification",
            "stringified JSON object for msg data to be used by the contract",
            listOf(CoinStr("100", "some_denom"))
        )
    )
}

open class BaseProposal

data class GovSubmitProposalRequest(
    val submitter: String,
    val title: String,
    val description: String,
    val content: String,
    val initialDeposit: List<CoinStr>
)

data class SoftwareUpgradeData(
    val name: String,
    val height: Long,
    val info: String
) : BaseProposal()

data class ParameterChangeData(
    val changes: List<ParamChangeObj>
) : BaseProposal()

data class ParamChangeObj(
    val subspace: String,
    val key: String,
    val value: String
)

data class StoreCodeData(
    val runAs: String,
    val accessConfig: StoreCodeAccessConfig? = null
) : BaseProposal()

data class StoreCodeAccessConfig(
    val type: Types.AccessType,
    val address: String? = null
)

data class InstantiateContractData(
    val runAs: String,
    val admin: String? = null,
    val codeId: Int,
    val label: String? = null,
    val msg: String,
    val funds: List<CoinStr> = emptyList()
) : BaseProposal()

data class GovDepositRequest(
    val proposalId: Long,
    val depositor: String,
    val deposit: List<CoinStr>
)

data class GovVoteRequest(
    val proposalId: Long,
    val voter: String,
    val votes: List<WeightedVoteOption>
)

data class WeightedVoteOption(
    val weight: Int,
    val option: Gov.VoteOption
)

//endregion

//region Staking Msg Models
data class StakingDelegateRequest(
    val delegator: String,
    val validator: String,
    val amount: CoinStr
)

data class StakingRedelegateRequest(
    val delegator: String,
    val validatorSrc: String,
    val validatorDst: String,
    val amount: CoinStr
)

data class StakingUndelegateRequest(
    val delegator: String,
    val validator: String,
    val amount: CoinStr
)

data class StakingCancelUnbondingRequest(
    val delegator: String,
    val validator: String,
    val amount: CoinStr,
    val unbondingCreateHeight: Int
)

data class StakingWithdrawRewardsRequest(
    val delegator: String,
    val validator: String
)

data class StakingWithdrawCommissionRequest(
    val validator: String
)

//endregion

//region Bank Msg Models
data class BankSendRequest(
    val from: String,
    val to: String,
    val funds: List<CoinStr>
)

//endregion
