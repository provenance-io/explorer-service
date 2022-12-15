package io.provenance.explorer.domain.models.explorer

import io.provenance.explorer.domain.core.sql.toArray
import io.provenance.explorer.domain.core.sql.toObject
import io.provenance.explorer.domain.entities.GovDepositTable
import io.provenance.explorer.domain.entities.GovProposalTable
import io.provenance.explorer.domain.entities.GovVoteTable
import io.provenance.explorer.domain.entities.GroupsProposalTable
import io.provenance.explorer.domain.entities.GroupsTable
import io.provenance.explorer.domain.entities.GroupsVoteTable
import io.provenance.explorer.domain.entities.IbcLedgerAckTable
import io.provenance.explorer.domain.entities.IbcLedgerTable
import io.provenance.explorer.domain.entities.ProposalMonitorTable
import io.provenance.explorer.domain.entities.SignatureTxTable
import io.provenance.explorer.domain.entities.TxAddressJoinTable
import io.provenance.explorer.domain.entities.TxFeeTable
import io.provenance.explorer.domain.entities.TxFeepayerTable
import io.provenance.explorer.domain.entities.TxGroupsPolicyTable
import io.provenance.explorer.domain.entities.TxGroupsTable
import io.provenance.explorer.domain.entities.TxIbcTable
import io.provenance.explorer.domain.entities.TxMarkerJoinTable
import io.provenance.explorer.domain.entities.TxNftJoinTable
import io.provenance.explorer.domain.entities.TxSingleMessageCacheTable
import io.provenance.explorer.domain.entities.TxSmCodeTable
import io.provenance.explorer.domain.entities.TxSmContractTable
import io.provenance.explorer.model.CustomFee
import io.provenance.explorer.model.MsgTypeSet
import io.provenance.explorer.model.TxStatus
import org.joda.time.DateTime
import java.math.BigDecimal

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
) {

    fun onlyTxQuery() =
        addressId == null && addressType == null && address == null &&
            markerId == null && denom == null &&
            msgTypes.isEmpty() &&
            nftId == null &&
            ibcChannelIds.isEmpty()
}

data class TxFeeData(
    val msgType: String,
    val amount: BigDecimal,
    val denom: String,
    val recipient: String?,
    val origFees: CustomFeeList?
)

data class CustomFeeList(val list: List<CustomFee>)

data class EventFee(
    val msg_type: String,
    val count: String,
    val total: String,
    val recipient: String? = null
)

data class TxHeatmapRaw(
    val dow: Int,
    val day: String,
    val hour: Int,
    val numberTxs: Int
)

fun String.getCategoryForType() = MsgTypeSet.values().firstOrNull { it.types.contains(this) }
fun MsgTypeSet?.getValuesPlusAddtnl() = this!!.types + this.additionalTypes

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
    var groupsList: MutableList<String> = mutableListOf(),
    var groupJoin: MutableList<String> = mutableListOf(),
    var groupPolicies: MutableList<String> = mutableListOf(),
    var policyJoinAlt: MutableList<String> = mutableListOf(),
    var groupProposals: MutableList<String> = mutableListOf(),
    var groupVotes: MutableList<String> = mutableListOf()
) {
    fun toProcedureObject() =
        listOf(
            this.tx,
            this.txGasFee!!,
            this.txFees.toArray(TxFeeTable.tableName),
            this.txMsgs.toArray("tx_msg"),
            this.singleMsgs.toArray(TxSingleMessageCacheTable.tableName),
            this.addressJoin.toArray(TxAddressJoinTable.tableName),
            this.markerJoin.toArray(TxMarkerJoinTable.tableName),
            this.nftJoin.toArray(TxNftJoinTable.tableName),
            this.ibcJoin.toArray(TxIbcTable.tableName),
            this.proposals.toArray(GovProposalTable.tableName),
            this.proposalMonitors.toArray(ProposalMonitorTable.tableName),
            this.deposits.toArray(GovDepositTable.tableName),
            this.votes.toArray(GovVoteTable.tableName),
            this.ibcLedgers.toArray(IbcLedgerTable.tableName),
            this.ibcLedgerAcks.toArray(IbcLedgerAckTable.tableName),
            this.smCodes.toArray(TxSmCodeTable.tableName),
            this.smContracts.toArray(TxSmContractTable.tableName),
            this.sigs.toArray(SignatureTxTable.tableName),
            this.feePayers.toArray(TxFeepayerTable.tableName),
            this.validatorMarketRate!!,
            this.groupsList.toArray(GroupsTable.tableName),
            this.groupJoin.toArray(TxGroupsTable.tableName),
            this.groupPolicies.toArray("groups_policy_data"),
            this.policyJoinAlt.toArray(TxGroupsPolicyTable.tableName),
            this.groupProposals.toArray(GroupsProposalTable.tableName),
            this.groupVotes.toArray(GroupsVoteTable.tableName)
        ).toObject()
}
data class BlockUpdate(
    var block: String,
    var proposer: String,
    var cache: String,
    var txs: List<String>
) {
    fun toProcedureObject() =
        listOf(this.block, this.proposer, this.cache, this.txs.toArray("tx_update")).toObject()
}
