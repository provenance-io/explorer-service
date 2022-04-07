package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.node.ObjectNode
import io.provenance.explorer.domain.core.sql.toArray
import io.provenance.explorer.domain.core.sql.toObject
import io.provenance.explorer.domain.entities.GovDepositTable
import io.provenance.explorer.domain.entities.GovProposalTable
import io.provenance.explorer.domain.entities.GovVoteTable
import io.provenance.explorer.domain.entities.IbcLedgerAckTable
import io.provenance.explorer.domain.entities.IbcLedgerTable
import io.provenance.explorer.domain.entities.ProposalMonitorTable
import io.provenance.explorer.domain.entities.SignatureJoinTable
import io.provenance.explorer.domain.entities.TxAddressJoinTable
import io.provenance.explorer.domain.entities.TxFeeTable
import io.provenance.explorer.domain.entities.TxFeepayerTable
import io.provenance.explorer.domain.entities.TxIbcTable
import io.provenance.explorer.domain.entities.TxMarkerJoinTable
import io.provenance.explorer.domain.entities.TxNftJoinTable
import io.provenance.explorer.domain.entities.TxSingleMessageCacheTable
import io.provenance.explorer.domain.entities.TxSmCodeTable
import io.provenance.explorer.domain.entities.TxSmContractTable
import org.joda.time.DateTime
import java.math.BigDecimal
import java.math.BigInteger

data class TxData(
    val blockHeight: Int,
    val txHashId: Int?,
    val txHash: String,
    val txTimestamp: DateTime
)

data class TxQueryParams(
    val addressId: Int? = null,
    val addressType: String? = null,
    val address: String? = null,
    val markerId: Int? = null,
    val denom: String? = null,
    val msgTypes: List<Int> = emptyList(),
    val txHeight: Int? = null,
    val txStatus: TxStatus? = null,
    val count: Int,
    val offset: Int,
    val fromDate: DateTime? = null,
    val toDate: DateTime? = null,
    val nftId: Int? = null,
    val nftType: String? = null,
    val nftUuid: String? = null,
    val smCodeId: Int? = null,
    val smContractAddrId: Int? = null,
    val ibcChannelIds: List<Int> = emptyList()
)

fun TxQueryParams.onlyTxQuery() =
    addressId == null && addressType == null && address == null &&
        markerId == null && denom == null &&
        msgTypes.isEmpty() &&
        nftId == null &&
        ibcChannelIds.isEmpty()

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
    val signers: Signatures,
    val memo: String,
    val monikers: Map<String, String>,
    val feepayer: TxFeepayer
)

data class TxFeepayer(val type: String, val address: String)

data class TxFee(val type: String, val fees: List<FeeCoinStr>)

data class FeeCoinStr(val amount: String, val denom: String, val msgType: String?)

data class Gas(
    val gasUsed: Long,
    val gasWanted: Long,
    val gasPrice: CoinStr
)

data class TxHistory(val date: String, val numberTxs: Int)

data class TxHeatmapRaw(
    val dow: Int,
    val day: String,
    val hour: Int,
    val numberTxs: Int
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

data class TxType(
    val module: String,
    val type: String
)

enum class MsgTypeSet(val mainCategory: String, val types: List<String>) {
    ACCOUNT(
        "account",
        listOf(
            "add_attribute",
            "delete_attribute",
            "grant_allowance",
            "revoke_allowance",
            "grant",
            "revoke",
            "exec"
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
            "withdraw_delegator_reward"
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
        listOf("store_code", "instantiate_contract", "execute_contract", "migrate_contract", "clear_admin")
    ),
    TRANSFER(
        "transfer",
        listOf("send", "multisend", "transfer", "ibc_transfer")
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
        )
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

fun String.getCategoryForType() = MsgTypeSet.values().firstOrNull { it.types.contains(this) }

data class TxSummary(
    val txHash: String,
    val block: Int,
    val msg: MsgInfo,
    val monikers: Map<String, String>,
    val time: String,
    val fee: CoinStr,
    val signers: Signatures,
    val status: String,
    val feepayer: TxFeepayer
)

data class MsgInfo(
    val msgCount: Long,
    val displayMsgType: String
)

data class TxMessage(
    val type: String,
    val msg: ObjectNode
)

enum class DateTruncGranularity { DAY, HOUR }

enum class TxStatus { SUCCESS, FAILURE }

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
    val signers: Signatures,
    val txStatus: String,
    val feepayer: TxFeepayer
)

data class TxGasVolume(
    val date: String,
    val gasWanted: BigInteger,
    val gasUsed: BigInteger,
    val feeAmount: BigDecimal
)

data class TxSmartContract(
    val txHash: String,
    val txMsgType: String,
    val smCode: Int,
    val smContractAddr: String?,
    val block: Int,
    val txTime: String,
    val txFee: CoinStr,
    val signers: Signatures,
    val txStatus: String,
    val feepayer: TxFeepayer
)

data class MsgProtoBreakout(
    val proto: String,
    val module: String,
    val type: String
)

data class TxUpdate(
    var tx: String,
    var txGasFee: String? = null,
    var txFees: MutableList<String> = mutableListOf(),
    var txMsgs: MutableList<String> = mutableListOf(),
    var singleMsgs: MutableList<String> = mutableListOf(),
    var addressJoin: MutableList<String> = mutableListOf(),
    var markerJoin: MutableList<String> = mutableListOf(),
    var nftJoin: MutableList<String> = mutableListOf(),
    var ibcJoin: MutableList<String> = mutableListOf(),
    var proposals: MutableList<String> = mutableListOf(),
    var proposalMonitors: MutableList<String> = mutableListOf(),
    var deposits: MutableList<String> = mutableListOf(),
    var votes: MutableList<String> = mutableListOf(),
    var ibcLedgers: MutableList<String> = mutableListOf(),
    var ibcLedgerAcks: MutableList<String> = mutableListOf(),
    var smCodes: MutableList<String> = mutableListOf(),
    var smContracts: MutableList<String> = mutableListOf(),
    var sigs: MutableList<String> = mutableListOf(),
    var feePayers: MutableList<String> = mutableListOf(),
    var validatorMarketRate: String? = null,
)

fun TxUpdate.toProcedureObject() =
    listOf(
        this.tx, this.txGasFee!!, this.txFees.toArray(TxFeeTable.tableName),
        this.txMsgs.toArray("tx_msg"), this.singleMsgs.toArray(TxSingleMessageCacheTable.tableName),
        this.addressJoin.toArray(TxAddressJoinTable.tableName), this.markerJoin.toArray(TxMarkerJoinTable.tableName),
        this.nftJoin.toArray(TxNftJoinTable.tableName), this.ibcJoin.toArray(TxIbcTable.tableName),
        this.proposals.toArray(GovProposalTable.tableName), this.proposalMonitors.toArray(ProposalMonitorTable.tableName),
        this.deposits.toArray(GovDepositTable.tableName), this.votes.toArray(GovVoteTable.tableName),
        this.ibcLedgers.toArray(IbcLedgerTable.tableName), this.ibcLedgerAcks.toArray(IbcLedgerAckTable.tableName),
        this.smCodes.toArray(TxSmCodeTable.tableName), this.smContracts.toArray(TxSmContractTable.tableName),
        this.sigs.toArray(SignatureJoinTable.tableName), this.feePayers.toArray(TxFeepayerTable.tableName),
        this.validatorMarketRate!!
    ).toObject()

data class BlockUpdate(
    var block: String,
    var proposer: String,
    var cache: String,
    var txs: List<String>
)

fun BlockUpdate.toProcedureObject() =
    listOf(this.block, this.proposer, this.cache, this.txs.toArray("tx_update")).toObject()
