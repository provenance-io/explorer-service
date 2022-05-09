package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.node.ObjectNode
import org.joda.time.DateTime

enum class GovParamType { voting, tallying, deposit }

data class GovAddrData(
    val addr: String,
    val addrId: Int,
    val isValidator: Boolean
)

data class GovTimeFrame(
    val startTime: String,
    val endTime: String
)

data class DepositPercentage(
    val initial: String,
    val current: String,
    val needed: String,
    val denom: String
)

data class GovProposalDetail(
    val header: ProposalHeader,
    val timings: ProposalTimings
)

data class ProposalHeader(
    val proposalId: Long,
    val status: String,
    val proposer: GovAddress,
    val type: String,
    val title: String,
    val description: String,
    val details: ObjectNode
)

data class GovAddress(
    val address: String,
    val validatorAddr: String?,
    val moniker: String?
)

data class ProposalTimings(
    val deposit: DepositPercentage,
    val voting: VotingDetails,
    val submitTime: String,
    val depositEndTime: String,
    val votingTime: GovTimeFrame
)

data class VotingDetails(
    val params: TallyParams,
    val tally: VotesTally
)

data class GovVotesDetail(
    val params: TallyParams,
    val tally: VotesTally,
    val votes: List<VoteRecord>
)

data class TallyParams(
    val totalEligibleAmount: CoinStr,
    val quorumThreshold: String,
    val passThreshold: String,
    val vetoThreshold: String
)

data class VotesTally(
    val yes: Tally,
    val no: Tally,
    val noWithVeto: Tally,
    val abstain: Tally,
    val total: Tally
)

data class Tally(
    val count: Int,
    val amount: CoinStr
)

data class VoteRecord(
    val voter: GovAddress,
    val answer: Map<String, Double?>,
    val blockHeight: Int,
    val txHash: String,
    val txTimestamp: String,
    val proposalId: Long,
    val proposalTitle: String,
    val proposalStatus: String
)

data class VoteDbRecord(
    val voter: String,
    val isValidator: Boolean,
    val vote: String,
    val weight: Double,
    val blockHeight: Int,
    val txHash: String,
    val txTimestamp: DateTime,
    val proposalId: Long,
    val proposalTitle: String,
    val proposalStatus: String
)

data class VoteDbRecordAgg(
    val voter: String,
    val isValidator: Boolean,
    val voteWeight: List<VoteWeightDbObj>,
    val blockHeight: Int,
    val txHash: String,
    val txTimestamp: DateTime,
    val proposalId: Long,
    val proposalTitle: String,
    val proposalStatus: String
)

data class DepositRecord(
    val voter: GovAddress,
    val type: String,
    val amount: CoinStr,
    val blockHeight: Int,
    val txHash: String,
    val txTimestamp: String
)

data class GovMsgDetail(
    val depositAmount: CoinStr?,
    var proposalType: String,
    val proposalId: Long,
    var proposalTitle: String
)

data class ProposalParamHeights(
    val depositCheckHeight: Int,
    val votingCheckHeight: Int
)

data class VoteWeightDbObj(val vote: String, val weight: Double)
