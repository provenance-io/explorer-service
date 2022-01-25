package io.provenance.explorer.service

import com.google.protobuf.Any
import com.google.protobuf.util.JsonFormat
import cosmos.gov.v1beta1.Gov
import cosmos.gov.v1beta1.Tx
import cosmwasm.wasm.v1beta1.Proposal
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.DepositType
import io.provenance.explorer.domain.entities.GovDepositRecord
import io.provenance.explorer.domain.entities.GovProposalRecord
import io.provenance.explorer.domain.entities.GovVoteRecord
import io.provenance.explorer.domain.entities.ProposalMonitorRecord
import io.provenance.explorer.domain.entities.ProposalType
import io.provenance.explorer.domain.entities.SmCodeRecord
import io.provenance.explorer.domain.entities.SpotlightCacheRecord
import io.provenance.explorer.domain.entities.ValidatorStateRecord
import io.provenance.explorer.domain.extensions.NHASH
import io.provenance.explorer.domain.extensions.formattedString
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.stringfy
import io.provenance.explorer.domain.extensions.to256Hash
import io.provenance.explorer.domain.extensions.toBase64
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toDecCoin
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.CoinStr
import io.provenance.explorer.domain.models.explorer.DepositPercentage
import io.provenance.explorer.domain.models.explorer.DepositRecord
import io.provenance.explorer.domain.models.explorer.GovAddrData
import io.provenance.explorer.domain.models.explorer.GovAddress
import io.provenance.explorer.domain.models.explorer.GovMsgDetail
import io.provenance.explorer.domain.models.explorer.GovParamType
import io.provenance.explorer.domain.models.explorer.GovProposalDetail
import io.provenance.explorer.domain.models.explorer.GovTimeFrame
import io.provenance.explorer.domain.models.explorer.GovVotesDetail
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.ProposalHeader
import io.provenance.explorer.domain.models.explorer.ProposalTimings
import io.provenance.explorer.domain.models.explorer.Tally
import io.provenance.explorer.domain.models.explorer.TallyParams
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.VoteDbRecord
import io.provenance.explorer.domain.models.explorer.VoteRecord
import io.provenance.explorer.domain.models.explorer.VotesTally
import io.provenance.explorer.domain.models.explorer.toData
import io.provenance.explorer.grpc.extensions.toMsgDeposit
import io.provenance.explorer.grpc.extensions.toMsgVote
import io.provenance.explorer.grpc.v1.GovGrpcClient
import io.provenance.explorer.grpc.v1.SmartContractGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class GovService(
    private val govClient: GovGrpcClient,
    private val protoPrinter: JsonFormat.Printer,
    private val valService: ValidatorService,
    private val smContractClient: SmartContractGrpcClient
) {
    protected val logger = logger(GovService::class)

    fun buildProposal(proposalId: Long, txInfo: TxData, addr: String, isSubmit: Boolean) = transaction {
        govClient.getProposal(proposalId).let {
            GovProposalRecord.buildInsert(it.proposal, protoPrinter, txInfo, getAddressDetails(addr), isSubmit)
        }
    }

    fun updateProposal(record: GovProposalRecord) = transaction {
        govClient.getProposal(record.proposalId).let { res ->
            if (res.proposal.status.name != record.status)
                record.apply { this.status = res.proposal.status.name }
        }
    }

    fun buildProposalMonitor(txMsg: Tx.MsgSubmitProposal, proposalId: Long, txInfo: TxData) = transaction {
        val (proposalType, dataHash) = when {
            txMsg.content.typeUrl.endsWith("v1beta1.StoreCodeProposal") ->
                ProposalType.STORE_CODE to
                    // base64(sha256(gzipUncompress(wasmByteCode))) == base64(storedCode.data_hash)
                    txMsg.content.unpack(Proposal.StoreCodeProposal::class.java)
                        .wasmByteCode.gzipUncompress().to256Hash()
            txMsg.content.typeUrl.endsWith("v1.StoreCodeProposal") ->
                ProposalType.STORE_CODE to
                    // base64(sha256(gzipUncompress(wasmByteCode))) == base64(storedCode.data_hash)
                    txMsg.content.unpack(cosmwasm.wasm.v1.Proposal.StoreCodeProposal::class.java)
                        .wasmByteCode.gzipUncompress().to256Hash()
            else -> return@transaction null
        }
        val votingEndTime = govClient.getProposal(proposalId)!!.proposal.votingEndTime.toDateTime()
        val avgBlockTime = SpotlightCacheRecord.getSpotlight().avgBlockTime.multiply(BigDecimal(1000)).toLong()
        val blocksUntilCompletion = (votingEndTime.millis - txInfo.txTimestamp.millis).floorDiv(avgBlockTime).toInt()

        ProposalMonitorRecord.buildInsert(
            proposalId,
            txInfo.blockHeight,
            txInfo.blockHeight + blocksUntilCompletion,
            votingEndTime,
            proposalType,
            dataHash
        )
    }

    fun processProposal(proposal: ProposalMonitorRecord) = transaction {
        when (ProposalType.valueOf(proposal.proposalType)) {
            ProposalType.STORE_CODE -> {
                val creationHeight = BlockCacheRecord.getFirstBlockAfterTime(proposal.votingEndTime).height
                val records = SmCodeRecord.all().sortedByDescending { it.id.value }
                val matching = records.firstOrNull { proposal.dataHash == it.dataHash }
                // find existing record and update, else search for next code id only.
                matching?.apply { this.creationHeight = creationHeight }
                    ?.also { proposal.apply { this.processed = true } }
                    ?: records.first().id.value.let { start ->
                        smContractClient.getSmCode(start.toLong() + 1)?.let {
                            if (it.codeInfo.dataHash.toBase64() == proposal.dataHash) {
                                SmCodeRecord.getOrInsert(start + 1, it, creationHeight)
                                proposal.apply { this.processed = true }
                            }
                        }
                    }
            }
        }
    }

    private fun getAddressDetails(addr: String) = transaction {
        val addrId = AccountRecord.findByAddress(addr)!!.id.value
        val isValidator = ValidatorStateRecord.findByAccount(addr) != null
        GovAddrData(addr, addrId, isValidator)
    }

    fun buildDeposit(proposalId: Long, txInfo: TxData, deposit: Tx.MsgDeposit?, initial: Tx.MsgSubmitProposal?) =
        transaction {
            val addrInfo = getAddressDetails(deposit?.depositor ?: initial!!.proposer)
            val amountList = deposit?.amountList?.toList() ?: initial!!.initialDepositList.toList()
            val depositType = if (deposit != null) DepositType.DEPOSIT else DepositType.INITIAL_DEPOSIT

            GovDepositRecord.buildInserts(txInfo, proposalId, depositType, amountList, addrInfo)
        }

    fun buildVote(txInfo: TxData, vote: Tx.MsgVote) =
        GovVoteRecord.buildInsert(txInfo, vote, getAddressDetails(vote.voter))

    private fun getParams(param: GovParamType) = govClient.getParams(param)

    private fun getDepositPercentage(proposalId: Long) = transaction {
        val (initial, current) = GovDepositRecord.findByProposalId(proposalId).filter { it.denom == NHASH }
            .let { list ->
                val current = list.sumOf { it.amount }
                val initial =
                    list.firstOrNull { it.depositType == DepositType.INITIAL_DEPOSIT.name }?.amount ?: BigDecimal.ZERO
                initial to current
            }
        val needed = getParams(GovParamType.deposit).depositParams.minDepositList.first { it.denom == NHASH }.amount
        DepositPercentage(initial.stringfy(), current.stringfy(), needed, NHASH)
    }

    private fun getAddressObj(addr: String, isValidator: Boolean) = transaction {
        val (operatorAddress, moniker) =
            if (isValidator) ValidatorStateRecord.findByAccount(addr)!!.let { it.operatorAddress to it.moniker }
            else null to null
        GovAddress(addr, operatorAddress, moniker)
    }

    private fun mapProposalRecord(record: GovProposalRecord) = GovProposalDetail(
        ProposalHeader(
            record.proposalId,
            record.status,
            getAddressObj(record.address, record.isValidator),
            record.proposalType,
            record.title,
            record.description,
            record.content
        ),
        ProposalTimings(
            getDepositPercentage(record.proposalId),
            record.data.submitTime.formattedString(),
            record.data.depositEndTime.formattedString(),
            GovTimeFrame(record.data.votingStartTime.formattedString(), record.data.votingEndTime.formattedString())
        )
    )

    fun getProposalsList(page: Int, count: Int) =
        GovProposalRecord.getAllPaginated(page.toOffset(count), count)
            .map { mapProposalRecord(it) }
            .let {
                val total = GovProposalRecord.getAllCount()
                PagedResults(total.pageCountOfResults(count), it, total)
            }

    fun getProposalDetail(proposalId: Long) = transaction {
        GovProposalRecord.findByProposalId(proposalId)?.let { mapProposalRecord(it) }
            ?: throw ResourceNotFoundException("Invalid proposal id: '$proposalId'")
    }

    fun getProposalVotes(proposalId: Long) = transaction {
        val params = getParams(GovParamType.tallying).tallyParams.let { param ->
            TallyParams(
                CoinStr(valService.getStakingValidators("active").sumOf { it.tokenCount }.stringfy(), NHASH),
                param.quorum.toStringUtf8().toDecCoin(),
                param.threshold.toStringUtf8().toDecCoin(),
                param.vetoThreshold.toStringUtf8().toDecCoin()
            )
        }
        val dbRecords = GovVoteRecord.findByProposalId(proposalId)
        val voteRecords = dbRecords.map { mapVoteRecord((it)) }
        val indTallies = dbRecords.groupingBy { it.answer }.eachCount()
        val tallies = govClient.getTally(proposalId).tally
        val tally = VotesTally(
            Tally(indTallies.getOrDefault(Gov.VoteOption.VOTE_OPTION_YES.name, 0), CoinStr(tallies.yes, NHASH)),
            Tally(indTallies.getOrDefault(Gov.VoteOption.VOTE_OPTION_NO.name, 0), CoinStr(tallies.no, NHASH)),
            Tally(
                indTallies.getOrDefault(Gov.VoteOption.VOTE_OPTION_NO_WITH_VETO.name, 0),
                CoinStr(tallies.noWithVeto, NHASH)
            ),
            Tally(indTallies.getOrDefault(Gov.VoteOption.VOTE_OPTION_ABSTAIN.name, 0), CoinStr(tallies.abstain, NHASH)),
            Tally(dbRecords.count(), CoinStr(tallies.sum().stringfy(), NHASH))
        )
        GovVotesDetail(params, tally, voteRecords)
    }

    fun Gov.TallyResult.sum() =
        this.yes.toBigDecimal().plus(this.no.toBigDecimal()).plus(this.noWithVeto.toBigDecimal())
            .plus(this.abstain.toBigDecimal())

    private fun mapVoteRecord(record: VoteDbRecord) = VoteRecord(
        getAddressObj(record.voter, record.isValidator),
        record.answer,
        record.blockHeight,
        record.txHash,
        record.txTimestamp.toString(),
        record.proposalId,
        record.proposalTitle,
        record.proposalStatus
    )

    fun getProposalDeposits(proposalId: Long, page: Int, count: Int) = transaction {
        GovDepositRecord.getByProposalIdPaginated(proposalId, count, page.toOffset(count)).map {
            DepositRecord(
                getAddressObj(it.address, it.isValidator),
                it.depositType,
                CoinStr(it.amount.stringfy(), it.denom),
                it.blockHeight,
                it.txHash,
                it.txTimestamp.toString()
            )
        }.let {
            val total = GovDepositRecord.getByProposalIdCount(proposalId)
            PagedResults(total.pageCountOfResults(count), it, total)
        }
    }

    fun getAddressVotes(address: String, page: Int, count: Int) = transaction {
        val addr = AccountRecord.findByAddress(address)
            ?: throw ResourceNotFoundException("Invalid account address: '$address'")
        GovVoteRecord.getByAddrIdPaginated(addr.id.value, count, page.toOffset(count)).map {
            mapVoteRecord(it)
        }.let {
            val total = GovVoteRecord.getByAddrIdCount(addr.id.value)
            PagedResults(total.pageCountOfResults(count), it, total)
        }
    }
}

fun Any.getGovMsgDetail(txHash: String) =
    when {
        typeUrl.endsWith("MsgSubmitProposal") ->
            transaction {
                val proposalId = GovProposalRecord.findByTxHash(txHash)!!.proposalId
                val deposit = GovDepositRecord.findByTxHash(txHash)!!
                GovMsgDetail(deposit.amount.toCoinStr(deposit.denom), "", proposalId, "")
            }
        typeUrl.endsWith("MsgVote") ->
            this.toMsgVote().let { GovMsgDetail(null, "", it.proposalId, "") }
        typeUrl.endsWith("MsgDeposit") ->
            this.toMsgDeposit().let { GovMsgDetail(it.amountList.first().toData(), "", it.proposalId, "") }
        else -> null.also { logger().debug("This typeUrl is not a governance-based msg: $typeUrl") }
    }?.let { detail ->
        transaction {
            GovProposalRecord.findByProposalId(detail.proposalId)!!.let {
                detail.apply {
                    this.proposalType = it.proposalType
                    this.proposalTitle = it.title
                }
            }
        }
    }
