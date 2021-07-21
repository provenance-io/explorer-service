package io.provenance.explorer.domain.models.explorer

import com.google.protobuf.ProtocolStringList

data class Params(
    val cosmos: CosmosParams,
    val prov: ProvParams,
)

data class CosmosParams(
    val auth: AuthParams,
    val bank: BankParams,
    val dist: DistParams,
    val gov: GovParams,
    val mint: MintParams,
    val slashing: SlashingParams,
    val staking: StakingParams,
    val ibc: IBCParams,
)

data class AuthParams(
    val max_memo_characters: Long,
    val txSigLimit: Long,
    val txSizeCostPerByte: Long,
    val sigVerifyCostEd25519: Long,
    val sigVerifyCostSecp256k1: Long,
)

data class BankParams(
    val defaultSendEnabled: Boolean,
)

data class DistParams(
    val communityTax: String,
    val baseProposerReward: String,
    val bonusProposerReward: String,
    val withdrawAddrEnabled: Boolean,
)

data class GovParams(
    val voting: VotingParams,
    val tallying: TallyingParams,
    val deposit: DepositParams,
)

data class VotingParams(
    val votingPeriod: Long,
)

data class TallyingParams(
    val quorum: String,
    val threshold: String,
    val vetoThreshold: String
)

data class DepositParams(
    val minDeposit: MinDeposit,
    val maxDepositPeriod: Long,
)

data class MinDeposit(
    val denom: String,
    val amount: String,
)

data class MintParams(
    val mintDenom: String,
    val inflationRateChange: String,
    val inflationMax: String,
    val inflationMin: String,
    val goalBonded: String,
    val blocksPerYear: Long,
)

data class SlashingParams(
    val signedBlocksWindow: Long,
    val minSignedPerWindow: String,
    val downtimeJailDuration: Long,
    val slashFractionDoubleSign: String,
    val slashFractionDowntime: String,
)

data class StakingParams(
    val unbondingTime: Long,
    val maxValidators: Int,
    val maxEntries: Int,
    val bondDenom: String,
)

data class IBCParams(
    val transfer: TransferParams,
    val client: ClientParams,
)

data class TransferParams(
    val sendEnabled: Boolean,
    val receiveEnabled: Boolean,
)

data class ClientParams(
    val allowedClientsList: ProtocolStringList,
)

data class ProvParams(
    val attribute: AttributeParams,
    val marker: MarkerParams,
    val metadata: String,
    val name: NameParams,
)

data class AttributeParams(
    val maxValueLength: Int,
)

data class MarkerParams(
    val maxTotalSupply: Long,
    val enableGovernance: Boolean,
    val unrestrictedDenomRegex: String,
)

data class NameParams(
    val maxSegmentLength: Int,
    val minSegmentLength: Int,
    val maxNameLevels: Int,
    val allowUnrestrictedNames: Boolean,
)
