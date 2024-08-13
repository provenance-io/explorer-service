package io.provenance.explorer.service

import cosmos.group.v1.Types
import cosmos.group.v1.Types.ProposalExecutorResult
import cosmos.group.v1.Types.ProposalStatus
import cosmos.group.v1.Types.Vote
import cosmos.group.v1.vote
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.domain.core.sql.toArray
import io.provenance.explorer.domain.core.sql.toObject
import io.provenance.explorer.domain.entities.*
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.models.explorer.*
import io.provenance.explorer.grpc.extensions.*
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

     fun saveGroups(tx: ServiceOuterClass.GetTxResponse, txInfo: TxData, txUpdate: TxUpdate) = transaction {
        // get groups, save
        val msgGroups = tx.tx.body.messagesList.mapNotNull { it.getAssociatedGroups() }
        val eventGroups = tx.txResponse.logsList
            .flatMap { it.eventsList }
            .filter { it.type in GroupEvents.values().map { grp -> grp.event } }
            .flatMap { e ->
                getGroupEventByEvent(e.type)!!.let {
                    e.attributesList
                        .filter { attr -> attr.key in it.idField }
                        .map { found -> found.value.scrubQuotes().toLong() }
                }
            }
        (msgGroups + eventGroups).toSet().forEach { id ->
            buildGroup(id, txInfo)?.let {
                txUpdate.apply {
                    if (tx.txResponse.code == 0) {
                        this.groupsList.add(it)
                    }
                    this.groupJoin.add(buildTxGroup(id, txInfo))
                }
            }
        }

        // get policies, save
        val msgPolicies = tx.tx.body.messagesList.mapNotNull { it.getAssociatedGroupPolicies() }
        val eventPolicies = tx.txResponse.logsList
            .flatMap { it.eventsList }
            .filter { it.type in GroupPolicyEvents.values().map { pol -> pol.event } }
            .flatMap { e ->
                getGroupPolicyEventByEvent(e.type)!!.let {
                    e.attributesList
                        .filter { attr -> attr.key in it.idField }
                        .map { found -> found.value.scrubQuotes() }
                }
            }
        (msgPolicies + eventPolicies).toSet().forEach { addr ->
            buildGroupPolicy(addr, txInfo)
                ?.also { ProcessQueueRecord.insertIgnore(ProcessQueueType.ACCOUNT, addr) }
                ?.let { policy ->
                    val (join, savedPolicy) = buildTxGroupPolicy(addr, txInfo)
                    txUpdate.apply {
                        if (tx.txResponse.code == 0) {
                            this.groupPolicies
                                .add(listOf(policy, listOf(join).toArray(TxGroupsPolicyTable.tableName)).toObject())
                        } else if (savedPolicy) {
                            this.policyJoinAlt.add(join)
                        }
                    }
                }
        }

        val govProposalMsgTuples = tx.tx.body.messagesList.mapIndexedNotNull { idx, msg -> msg.getAssociatedGroupProposals(idx) }
        if (tx.txResponse.code == 0) {
            govProposalMsgTuples.forEach { triple ->
                when (triple.second) {
                    GroupGovMsgType.PROPOSAL -> {
                        // Have to find the proposalId in the log events
                        val proposalId = tx.mapEventAttrValues(
                            triple.first,
                            GroupProposalEvents.GROUP_SUBMIT_PROPOSAL.event,
                            GroupProposalEvents.GROUP_SUBMIT_PROPOSAL.idField.toList()
                        )[GroupProposalEvents.GROUP_SUBMIT_PROPOSAL.idField.first()]!!.toLong()

                        val msg = triple.third.toMsgSubmitProposalGroup()
                        val nodeData = proposalAtHeight(proposalId, txInfo.blockHeight)?.proposal
                        val policy = policyAtHeight(msg.groupPolicyAddress, txInfo.blockHeight)!!.info
                        val group = groupAtHeight(policy.groupId, txInfo.blockHeight)!!.info
                        val tally = proposalTallyAtHeight(proposalId, txInfo.blockHeight)?.tally
                        val data = GroupsProposalData(
                            msg.proposersList,
                            msg.metadata,
                            msg.messagesList,
                            msg.exec.name,
                            txInfo.txTimestamp,
                            group.version,
                            policy.version,
                            tally,
                            nodeData?.votingPeriodEnd?.toDateTime()
                        )
                        val status = nodeData?.status ?: ProposalStatus.PROPOSAL_STATUS_ACCEPTED
                        val execResult = nodeData?.executorResult ?: tx.mapEventAttrValues(
                            triple.first,
                            GroupProposalEvents.GROUP_EXEC.event,
                            listOf("result")
                        )["result"]!!.getGroupsExecutorResult()
                        val proposal = buildProposal(
                            group.id,
                            policy.address,
                            proposalId,
                            data,
                            nodeData,
                            status,
                            execResult,
                            txInfo
                        )

                        val votes = buildVotes(
                            group.id,
                            group.version,
                            msg.proposersList,
                            vote {
                                this.proposalId = proposalId
                                this.option = Types.VoteOption.VOTE_OPTION_YES
                                this.metadata = ""
                            },
                            txInfo
                        )
                        txUpdate.apply {
                            this.groupProposals.add(proposal)
                            this.groupVotes.addAll(votes)
                            this.policyJoinAlt.add(buildTxGroupPolicy(msg.groupPolicyAddress, txInfo).first)
                        }
                    }

                    GroupGovMsgType.VOTE -> {
                        val msg = triple.third.toMsgVoteGroup()
                        val proposal = getProposalById(msg.proposalId)!!

                        buildVotes(
                            proposal.groupId.toLong(),
                            proposal.proposalData.groupVersion,
                            listOf(msg.voter),
                            vote {
                                this.proposalId = msg.proposalId
                                this.option = msg.option
                                this.metadata = msg.metadata
                            },
                            txInfo
                        ).let {
                            txUpdate.apply {
                                this.groupVotes.addAll(it)
                                this.policyJoinAlt.add(buildTxGroupPolicy(proposal.policyAddress, txInfo).first)
                            }
                        }

                        val execResult = tx.mapEventAttrValues(
                            triple.first,
                            GroupProposalEvents.GROUP_EXEC.event,
                            listOf("result")
                        )["result"]?.getGroupsExecutorResult()

                        if (execResult != null) {
                            transaction {
                                proposal.apply {
                                    if (proposal.proposalStatus.getGroupsProposalStatus() != ProposalStatus.PROPOSAL_STATUS_ACCEPTED) {
                                        this.proposalStatus = ProposalStatus.PROPOSAL_STATUS_ACCEPTED.name
                                    }
                                    if (proposal.executorResult.getGroupsExecutorResult() != execResult) {
                                        this.executorResult = execResult.name
                                    }
                                }
                            }
                        }
                    }

                    GroupGovMsgType.EXEC -> {
                        val msg = triple.third.toMsgExecGroup()
                        val proposal = getProposalById(msg.proposalId)!!

                        txUpdate.apply {
                            this.policyJoinAlt.add(buildTxGroupPolicy(proposal.policyAddress, txInfo).first)
                        }

                        val execResult = tx.mapEventAttrValues(
                            triple.first,
                            GroupProposalEvents.GROUP_EXEC.event,
                            listOf("result")
                        )["result"]?.getGroupsExecutorResult()

                        if (execResult != null) {
                            transaction {
                                proposal.apply {
                                    if (proposal.proposalStatus.getGroupsProposalStatus() != ProposalStatus.PROPOSAL_STATUS_ACCEPTED) {
                                        this.proposalStatus = ProposalStatus.PROPOSAL_STATUS_ACCEPTED.name
                                    }
                                    if (proposal.executorResult.getGroupsExecutorResult() != execResult) {
                                        this.executorResult = execResult.name
                                    }
                                }
                            }
                        }
                    }

                    GroupGovMsgType.WITHDRAW -> {
                        val msg = triple.third.toMsgWithdrawProposalGroup()
                        val proposal = getProposalById(msg.proposalId)!!

                        txUpdate.apply {
                            this.policyJoinAlt.add(buildTxGroupPolicy(proposal.policyAddress, txInfo).first)
                        }

                        transaction {
                            proposal.apply { this.proposalStatus = ProposalStatus.PROPOSAL_STATUS_WITHDRAWN.name }
                        }
                    }
                }
            }
        } else {
            govProposalMsgTuples.forEachIndexed { _, triple ->
                when (triple.second) {
                    GroupGovMsgType.PROPOSAL -> {
                        val msg = triple.third.toMsgSubmitProposalGroup()
                        txUpdate.apply {
                            this.policyJoinAlt.add(buildTxGroupPolicy(msg.groupPolicyAddress, txInfo).first)
                        }
                    }

                    GroupGovMsgType.VOTE -> {
                        val msg = triple.third.toMsgVoteGroup()
                        val proposal = getProposalById(msg.proposalId)!!
                        txUpdate.apply {
                            this.policyJoinAlt.add(buildTxGroupPolicy(proposal.policyAddress, txInfo).first)
                        }
                    }

                    GroupGovMsgType.EXEC -> {
                        val msg = triple.third.toMsgExecGroup()
                        val proposal = getProposalById(msg.proposalId)!!
                        txUpdate.apply {
                            this.policyJoinAlt.add(buildTxGroupPolicy(proposal.policyAddress, txInfo).first)
                        }
                    }

                    GroupGovMsgType.WITHDRAW -> {
                        val msg = triple.third.toMsgWithdrawProposalGroup()
                        val proposal = getProposalById(msg.proposalId)!!
                        txUpdate.apply {
                            this.policyJoinAlt.add(buildTxGroupPolicy(proposal.policyAddress, txInfo).first)
                        }
                    }
                }
            }
        }
    }
}

