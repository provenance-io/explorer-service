package io.provenance.explorer.service

import cosmos.group.v1.Types
import cosmos.group.v1.Types.ProposalExecutorResult
import cosmos.group.v1.Types.ProposalStatus
import cosmos.group.v1.Types.Vote
import cosmos.group.v1.vote
import io.provenance.explorer.domain.entities.GroupsHistoryRecord
import io.provenance.explorer.domain.entities.GroupsPolicyRecord
import io.provenance.explorer.domain.entities.GroupsProposalRecord
import io.provenance.explorer.domain.entities.GroupsRecord
import io.provenance.explorer.domain.entities.GroupsVoteRecord
import io.provenance.explorer.domain.entities.TxGroupsPolicyRecord
import io.provenance.explorer.domain.entities.TxGroupsRecord
import io.provenance.explorer.domain.models.explorer.GroupMembers
import io.provenance.explorer.domain.models.explorer.GroupsProposalData
import io.provenance.explorer.domain.models.explorer.GroupsProposalInsertData
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.grpc.v1.GroupGrpcClient
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class GroupService(
    private val groupClient: GroupGrpcClient,
    private val accountService: AccountService
) {
    fun groupAtHeight(groupId: Long, height: Int) = runBlocking {
        groupClient.getGroupByIdAtHeight(groupId, height)
    }

    fun groupMembersAHeight(groupId: Long, height: Int) = runBlocking {
        groupClient.getMembersByGroupAtHeight(groupId, height)
    }

    fun buildGroup(groupId: Long, txData: TxData) =
        groupAtHeight(groupId, txData.blockHeight)
            ?.let { res ->
                val members = groupMembersAHeight(groupId, txData.blockHeight)
                GroupsRecord.buildInsert(res.info, GroupMembers(members), txData)
            }

    fun buildTxGroup(groupId: Long, txData: TxData) = TxGroupsRecord.buildInsert(txData, groupId.toInt())

    fun policyAtHeight(policyAddr: String, height: Int) = runBlocking {
        groupClient.getPolicyByAddrAtHeight(policyAddr, height)
    }

    fun buildGroupPolicy(policyAddr: String, txData: TxData) =
        policyAtHeight(policyAddr, txData.blockHeight)
            ?.let { res ->
                accountService.saveAccount(policyAddr, false, true)
                GroupsPolicyRecord.buildInsert(res.info, txData)
            }

    fun buildTxGroupPolicy(policyAddr: String, txData: TxData) =
        GroupsPolicyRecord.findByPolicyAddr(policyAddr)?.id?.value
            .let { TxGroupsPolicyRecord.buildInsert(txData, it, policyAddr) to (it != null) }

    fun proposalTallyAtHeight(proposalId: Long, height: Int) = runBlocking {
        groupClient.getProposalTallyAtHeight(proposalId, height)
    }

    fun proposalAtHeight(proposalId: Long, height: Int) = runBlocking {
        groupClient.getProposalAtHeight(proposalId, height)
    }

    fun getProposalById(proposalId: Long) = GroupsProposalRecord.getById(proposalId)

    fun buildProposal(
        groupId: Long,
        policyAddr: String,
        proposalId: Long,
        data: GroupsProposalData,
        nodeData: Types.Proposal?,
        status: ProposalStatus,
        result: ProposalExecutorResult,
        txInfo: TxData
    ) = transaction {
        val policy = GroupsPolicyRecord.findByPolicyAddr(policyAddr)
        GroupsProposalRecord.buildInsert(
            GroupsProposalInsertData(groupId, policy!!, proposalId, data, nodeData, status, result),
            txInfo
        )
    }

    fun buildVotes(
        groupId: Long,
        groupVer: Long,
        addresses: List<String>,
        vote: Vote,
        txInfo: TxData
    ) = transaction {
        val members = (
            GroupsHistoryRecord.getByIdAndVersion(groupId.toInt(), groupVer.toInt())?.groupMembers?.list
                ?: groupMembersAHeight(groupId, txInfo.blockHeight)
            )
            .associate { it.address to BigDecimal(it.weight) }
        addresses.map { addr ->
            val addrData = accountService.getAddressDetails(addr)
            GroupsVoteRecord.buildInsert(addrData, vote, members[addr]!!, txInfo)
        }
    }
}
