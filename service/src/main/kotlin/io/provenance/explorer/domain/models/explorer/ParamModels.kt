package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.JsonNode
import io.provenance.explorer.JSON_NODE_FACTORY

data class Params(
    val cosmos: CosmosParams = CosmosParams(),
    val prov: ProvParams = ProvParams(),
)

data class CosmosParams(
    val auth: JsonNode = JSON_NODE_FACTORY.objectNode(),
    val bank: JsonNode = JSON_NODE_FACTORY.objectNode(),
    val dist: DistParams = DistParams(),
    val gov: GovParams = GovParams(),
    val mint: MintParams = MintParams(),
    val slashing: SlashingParams = SlashingParams(),
    val staking: JsonNode = JSON_NODE_FACTORY.objectNode(),
    val ibc: IBCParams = IBCParams(),
    val wasm: JsonNode = JSON_NODE_FACTORY.objectNode()
)

data class DistParams(
    val community_tax: String = "",
    val base_proposer_reward: String = "",
    val bonus_proposer_reward: String = "",
    val withdraw_addr_enabled: Boolean = false,
)

data class GovParams(
    val voting: JsonNode = JSON_NODE_FACTORY.objectNode(),
    val tallying: TallyingParams = TallyingParams(),
    val deposit: JsonNode = JSON_NODE_FACTORY.objectNode(),
)

data class TallyingParams(
    val quorum: String = "",
    val threshold: String = "",
    val veto_threshold: String = ""
)

data class MintParams(
    val mint_denom: String = "",
    val inflation_rate_change: String = "",
    val inflation_max: String = "",
    val inflation_min: String = "",
    val goal_bonded: String = "",
    val blocks_per_year: Long = 0L,
)

data class SlashingParams(
    val signed_blocks_window: Long = 0L,
    val min_signed_per_window: String = "",
    val downtime_jail_duration: String = "",
    val slash_fraction_double_sign: String = "",
    val slash_fraction_downtime: String = "",
)

data class IBCParams(
    val transfer: JsonNode = JSON_NODE_FACTORY.objectNode(),
    val client: JsonNode = JSON_NODE_FACTORY.objectNode(),
    val icaController: JsonNode = JSON_NODE_FACTORY.objectNode(),
    val icaHost: JsonNode = JSON_NODE_FACTORY.objectNode()
)

data class ProvParams(
    val attribute: JsonNode = JSON_NODE_FACTORY.objectNode(),
    val marker: JsonNode = JSON_NODE_FACTORY.objectNode(),
    val metadata: JsonNode = JSON_NODE_FACTORY.objectNode(),
    val name: JsonNode = JSON_NODE_FACTORY.objectNode(),
    val msgFees: JsonNode = JSON_NODE_FACTORY.objectNode()
)
