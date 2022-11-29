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
    val wasm: JsonNode
)

data class DistParams(
    val community_tax: String,
    val base_proposer_reward: String,
    val bonus_proposer_reward: String,
    val withdraw_addr_enabled: Boolean,
)

data class GovParams(
    val voting: JsonNode,
    val tallying: TallyingParams,
    val deposit: JsonNode,
)

data class TallyingParams(
    val quorum: String,
    val threshold: String,
    val veto_threshold: String
)

data class MintParams(
    val mint_denom: String,
    val inflation_rate_change: String,
    val inflation_max: String,
    val inflation_min: String,
    val goal_bonded: String,
    val blocks_per_year: Long,
)

data class SlashingParams(
    val signed_blocks_window: Long,
    val min_signed_per_window: String,
    val downtime_jail_duration: String,
    val slash_fraction_double_sign: String,
    val slash_fraction_downtime: String,
)

data class IBCParams(
    val transfer: JsonNode,
    val client: JsonNode,
    val icaController: JsonNode?,
    val icaHost: JsonNode
)

data class ProvParams(
    val attribute: JsonNode,
    val marker: JsonNode,
    val metadata: JsonNode,
    val name: JsonNode,
    val msgFees: JsonNode
)
