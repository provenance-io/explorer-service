package io.provenance.explorer.model

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.provenance.explorer.model.base.CoinStr
import java.math.BigDecimal

data class TxSummary(
    val txHash: String,
    val block: Int,
    val msg: MsgInfo,
    val monikers: Map<String, String>,
    val time: String,
    val fee: CoinStr,
    val signers: List<TxSignature>,
    val status: String,
    val feepayer: TxFeepayer
)

data class MsgInfo(
    val msgCount: Long,
    val displayMsgType: String
)

data class TxSignature(
    val idx: Int,
    val type: String,
    val address: String,
    val sequence: Int
)

data class TxFeepayer(val type: String, val address: String)

data class TxDetails(
    val txHash: String,
    val height: Int,
    val gas: Gas,
    val time: String,
    val status: String,
    val errorCode: Int?,
    val codespace: String?,
    val errorLog: String?,
    val fee: List<TxFee>,
    val signers: List<TxSignature>,
    val memo: String,
    val monikers: Map<String, String>,
    val feepayer: TxFeepayer,
    val associatedValues: List<TxAssociatedValues>,
    var additionalHeights: List<Int> = emptyList(),
    val events: List<JsonNode>
)

data class Gas(
    val gasUsed: Long,
    val gasWanted: Long,
    val gasPrice: CoinStr
)

data class TxFee(val type: String, val fees: List<FeeCoinStr>)

data class FeeCoinStr(
    val amount: String,
    val denom: String,
    val msgType: String?,
    val recipient: String?,
    val origFees: List<CustomFee>?
)

data class CustomFee(
    val name: String,
    val amount: BigDecimal,
    val denom: String,
    val recipient: String
)

data class TxAssociatedValues(val value: String, val type: String)

data class TxMessage(
    val type: String,
    val msg: ObjectNode,
    val logs: List<JsonNode>
)

data class TxHeatmapDay(
    val day: String,
    val numberTxs: Int
)

data class TxHeatmapHour(
    val hour: Int,
    val numberTxs: Int
)

data class TxHeatmap(
    val dow: Int,
    val day: String,
    val data: List<TxHeatmapHour>
)

data class TxHeatmapRes(
    val heatmap: List<TxHeatmap>,
    val dailyTotal: List<TxHeatmapDay>,
    val hourlyTotal: List<TxHeatmapHour>
)

data class TxHistory(val date: String, val numberTxs: Int)

data class TxType(
    val module: String,
    val type: String
)

enum class MsgTypeSet(val mainCategory: String, val types: List<String>, val additionalTypes: List<String> = emptyList()) {
    ACCOUNT(
        "account",
        listOf(
            "add_attribute",
            "update_attribute",
            "delete_attribute",
            "grant_allowance",
            "revoke_allowance",
            "grant",
            "revoke",
            "exec",
            "create_vesting_account"
        )
    ),
    DELEGATION(
        "staking",
        listOf(
            "begin_redelegate",
            "undelegate",
            "delegate",
            "fund_community_pool",
            "set_withdraw_address",
            "withdraw_delegator_reward",
            "cancel_unbonding_delegation"
        )
    ),
    VALIDATION(
        "staking",
        listOf("create_validator", "edit_validator", "unjail", "withdraw_validator_commission")
    ),
    GOVERNANCE(
        "governance",
        listOf("submit_proposal", "deposit", "vote", "vote_weighted")
    ),
    SMART_CONTRACT(
        "smart_contract",
        listOf(
            "store_code",
            "instantiate_contract",
            "execute_contract",
            "migrate_contract",
            "clear_admin",
            "update_admin"
        )
    ),
    TRANSFER(
        "transfer",
        listOf("send", "multi_send", "transfer", "ibc_transfer"),
        listOf("execute_contract")
    ),
    ASSET(
        "asset",
        listOf(
            "add_marker",
            "add_access",
            "delete_access",
            "finalize",
            "activate",
            "cancel",
            "delete",
            "mint",
            "burn",
            "withdraw",
            "set_denom_metadata",
        ),
        listOf("instantiate_contract")
    ),
    NFT(
        "nft",
        listOf(
            "p8e_memorialize_contract",
            "write_p8e_contract_spec",
            "write_scope",
            "delete_scope",
            "add_scope_data_access",
            "delete_scope_data_access",
            "add_scope_owner",
            "delete_scope_owner",
            "write_session",
            "write_record",
            "delete_record",
            "write_scope_specification",
            "delete_scope_specification",
            "write_contract_specification",
            "delete_contract_specification",
            "write_record_specification",
            "delete_record_specification",
            "write_os_locator",
            "delete_os_locator",
            "modify_os_locator",
            "add_contract_spec_to_scope_spec",
            "delete_contract_spec_from_scope_spec"
        )
    ),
    IBC(
        "ibc",
        listOf(
            "update_client",
            "channel_open_confirm",
            "channel_open_try",
            "channel_open_ack",
            "channel_open_init",
            "connection_open_confirm",
            "connection_open_try",
            "connection_open_ack",
            "connection_open_init",
            "channel_close_init",
            "channel_close_confirm",
            "create_client",
            "upgrade_client",
            "client_misbehaviour",
            "acknowledgement",
            "recv_packet",
            "ibc_transfer",
            "wasm-ibc-close",
            "wasm-ibc-send",
            "timeout",
            "timeout_on_close"
        )
    )
}

data class TxGov(
    val txHash: String,
    val txMsgType: String,
    val depositAmount: CoinStr?,
    val proposalType: String,
    val proposalId: Long,
    val proposalTitle: String,
    val block: Int,
    val txTime: String,
    val txFee: CoinStr,
    val signers: List<TxSignature>,
    val txStatus: String,
    val feepayer: TxFeepayer
)

data class TxSmartContract(
    val txHash: String,
    val txMsgType: String,
    val smCode: Int,
    val smContractAddr: String?,
    val block: Int,
    val txTime: String,
    val txFee: CoinStr,
    val signers: List<TxSignature>,
    val txStatus: String,
    val feepayer: TxFeepayer
)

enum class TxStatus { SUCCESS, FAILURE }
