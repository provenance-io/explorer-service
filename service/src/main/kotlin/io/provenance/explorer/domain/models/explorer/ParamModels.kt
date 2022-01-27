package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.JsonNode

data class Params(
    val cosmos: CosmosParams,
    val prov: ProvParams,
)

data class CosmosParams(
    val auth: JsonNode,
    val bank: JsonNode,
    val dist: DistParams,
    val gov: GovParams,
    val mint: MintParams,
    val slashing: SlashingParams,
    val staking: JsonNode,
    val ibc: IBCParams,
)

data class DistParams(
    val communityTax: String,
    val baseProposerReward: String,
    val bonusProposerReward: String,
    val withdrawAddrEnabled: Boolean,
)

data class GovParams(
    val voting: JsonNode,
    val tallying: TallyingParams,
    val deposit: JsonNode,
)

data class TallyingParams(
    val quorum: String,
    val threshold: String,
    val vetoThreshold: String
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
    val downtimeJailDuration: String,
    val slashFractionDoubleSign: String,
    val slashFractionDowntime: String,
)

data class IBCParams(
    val transfer: JsonNode,
    val client: JsonNode,
)

data class ProvParams(
    val attribute: JsonNode,
    val marker: JsonNode,
    val metadata: JsonNode,
    val name: JsonNode,
)
