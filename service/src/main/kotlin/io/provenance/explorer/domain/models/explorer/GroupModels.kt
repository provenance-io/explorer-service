package io.provenance.explorer.domain.models.explorer

import com.google.protobuf.Any
import cosmos.group.v1.Types
import cosmos.group.v1.Types.Proposal
import cosmos.group.v1.Types.ProposalExecutorResult
import cosmos.group.v1.Types.ProposalStatus
import cosmos.group.v1.Types.VoteOption
import org.joda.time.DateTime

data class GroupMembers(val list: MutableList<Types.Member>)

data class GroupsProposalData(
    val proposers: List<String>,
    val metadata: String,
    val messages: List<Any>,
    val execType: String,
    val submitTime: DateTime,
    val groupVersion: Long,
    val policyVersion: Long,
    val finalTallyResult: Types.TallyResult? = null,
    val votingPeriodEnd: DateTime? = null
)

data class GroupsProposalInsertData(
    val groupId: Long,
    val policyAddress: String,
    val proposalId: Long,
    val data: GroupsProposalData,
    val nodeData: Proposal? = null,
    val status: ProposalStatus,
    val result: ProposalExecutorResult
)

data class GroupsVoteInsertData(
    val proposalId: Long,
    val vote: VoteOption,
    val metadata: String
)
