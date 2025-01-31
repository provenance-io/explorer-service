package io.provenance.explorer.domain.models.explorer

import com.google.protobuf.Any
import com.google.protobuf.Timestamp
import io.provenance.explorer.model.base.CoinStr
import java.time.LocalDateTime

enum class GovParamType { voting, tallying, deposit }

data class AddrData(
    val addr: String,
    val addrId: Int,
    val isValidator: Boolean
)

data class VoteDbRecord(
    val voter: String,
    val isValidator: Boolean,
    val vote: String,
    val weight: Double,
    val blockHeight: Int,
    val txHash: String,
    val txTimestamp: LocalDateTime,
    val proposalId: Long,
    val proposalTitle: String,
    val proposalStatus: String,
    val justification: String?
)

data class VoteDbRecordAgg(
    val voter: String,
    val isValidator: Boolean,
    val voteWeight: List<VoteWeightDbObj>,
    val blockHeight: Int,
    val txHash: String,
    val txTimestamp: LocalDateTime,
    val proposalId: Long,
    val proposalTitle: String,
    val proposalStatus: String,
    val justification: String?
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

data class GovProposalMetadata(
    val title: String,
    val authors: String,
    val summary: String,
    val details: String,
    val proposal_forum_url: String,
    val vote_option_context: String
)

data class GovContentV1List(val list: List<Any>)

data class ProposalTimingData(
    val submitTime: Timestamp,
    val depositEndTime: Timestamp,
    val voteStart: Timestamp,
    val voteEnd: Timestamp
)

data class GovVoteMetadata(val justification: String)
