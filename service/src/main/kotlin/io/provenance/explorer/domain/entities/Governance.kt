package io.provenance.explorer.domain.entities

import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import cosmos.base.v1beta1.CoinOuterClass
import cosmos.gov.v1beta1.Gov
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.core.sql.toProcedureObject
import io.provenance.explorer.domain.models.explorer.GovAddrData
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.VoteDbRecord
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

object GovProposalTable : IntIdTable(name = "gov_proposal") {
    val proposalId = long("proposal_id")
    val proposalType = varchar("proposal_type", 128)
    val addressId = integer("address_id")
    val address = varchar("address", 128)
    val isValidator = bool("is_validator").default(false)
    val title = text("title")
    val description = text("description")
    val status = varchar("status", 128)
    val data = jsonb<GovProposalTable, Gov.Proposal>("data", OBJECT_MAPPER)
    val content = jsonb<GovProposalTable, ObjectNode>("content", OBJECT_MAPPER)
    val blockHeight = integer("block_height")
    val txHash = varchar("tx_hash", 64)
    val txTimestamp = datetime("tx_timestamp")
    val txHashId = reference("tx_hash_id", TxCacheTable)
}

fun String.getProposalType() = this.split(".").last().replace("Proposal", "")

fun Message.toProposalTitleAndDescription(protoPrinter: JsonFormat.Printer) =
    OBJECT_MAPPER.readValue(protoPrinter.print(this), ObjectNode::class.java)
        .let { it.get("title").asText() to it.get("description").asText() }

fun Message.toProposalContent(protoPrinter: JsonFormat.Printer) =
    OBJECT_MAPPER.readValue(protoPrinter.print(this), ObjectNode::class.java)
        .let { node ->
            node.remove("title")
            node.remove("description")
            node
        }

class GovProposalRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<GovProposalRecord>(GovProposalTable) {

        fun getAllCount() = transaction { GovProposalRecord.all().count() }

        fun getAllPaginated(offset: Int, limit: Int) = transaction {
            GovProposalRecord.all()
                .orderBy(Pair(GovProposalTable.proposalId, SortOrder.DESC))
                .limit(limit, offset.toLong())
                .toList()
        }

        fun findByTxHash(txHash: String) = transaction {
            GovProposalRecord.find { GovProposalTable.txHash eq txHash }.firstOrNull()
        }

        fun findByProposalId(proposalId: Long) = transaction {
            GovProposalRecord.find { GovProposalTable.proposalId eq proposalId }.firstOrNull()
        }

        fun findByProposalType(type: String) = transaction {
            GovProposalRecord.find { GovProposalTable.proposalType eq type.getProposalType() }
                .orderBy(Pair(GovProposalTable.proposalId, SortOrder.ASC))
                .toList()
        }

        fun getNonFinalProposals() = transaction {
            GovProposalRecord.find {
                GovProposalTable.status notInList listOf(
                    Gov.ProposalStatus.PROPOSAL_STATUS_FAILED.name,
                    Gov.ProposalStatus.PROPOSAL_STATUS_PASSED.name,
                    Gov.ProposalStatus.PROPOSAL_STATUS_REJECTED.name
                )
            }.toList()
        }

        fun buildInsert(
            proposal: Gov.Proposal,
            protoPrinter: JsonFormat.Printer,
            txInfo: TxData,
            addrInfo: GovAddrData,
            isSubmit: Boolean
        ) = transaction {
            proposal.content.toProposalTitleAndDescription(protoPrinter).let { (title, description) ->
                val (hash, block, time) = findByProposalId(proposal.proposalId)?.let {
                    if (isSubmit) Triple(txInfo.txHash, txInfo.blockHeight, txInfo.txTimestamp)
                    else Triple(it.txHash, it.blockHeight, it.txTimestamp)
                } ?: Triple(txInfo.txHash, txInfo.blockHeight, txInfo.txTimestamp)
                listOf(
                    -1,
                    proposal.proposalId,
                    proposal.content.typeUrl.getProposalType(),
                    addrInfo.addrId,
                    addrInfo.addr,
                    addrInfo.isValidator,
                    title,
                    description,
                    proposal.status.name,
                    proposal,
                    proposal.content.toProposalContent(protoPrinter),
                    block,
                    hash,
                    time,
                    0
                ).toProcedureObject()
            }
        }
    }

    var proposalId by GovProposalTable.proposalId
    var proposalType by GovProposalTable.proposalType
    var addressId by GovProposalTable.addressId
    var address by GovProposalTable.address
    var isValidator by GovProposalTable.isValidator
    var title by GovProposalTable.title
    var description by GovProposalTable.description
    var status by GovProposalTable.status
    var data by GovProposalTable.data
    var content by GovProposalTable.content
    var blockHeight by GovProposalTable.blockHeight
    var txHash by GovProposalTable.txHash
    var txTimestamp by GovProposalTable.txTimestamp
    var txHashId by TxCacheRecord referencedOn GovProposalTable.txHashId
}

object GovVoteTable : IntIdTable(name = "gov_vote") {
    val proposalId = long("proposal_id")
    val addressId = integer("address_id")
    val address = varchar("address", 128)
    val isValidator = bool("is_validator").default(false)
    val vote = varchar("vote", 128)
    val weight = decimal("weight", 3, 2)
    val blockHeight = integer("block_height")
    val txHash = varchar("tx_hash", 64)
    val txTimestamp = datetime("tx_timestamp")
    val txHashId = reference("tx_hash_id", TxCacheTable)
}

class GovVoteRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<GovVoteRecord>(GovVoteTable) {

        fun findByProposalId(proposalId: Long) = transaction {
            GovVoteRecord.find { GovVoteTable.proposalId eq proposalId }
                .orderBy(Pair(GovVoteTable.blockHeight, SortOrder.DESC))
                .map {
                    VoteDbRecord(
                        it.address,
                        it.isValidator,
                        it.vote,
                        it.weight.toDouble(),
                        it.blockHeight,
                        it.txHash,
                        it.txTimestamp,
                        it.proposalId,
                        "",
                        ""
                    )
                }
        }

        fun getByAddrIdPaginated(addrId: Int, limit: Int, offset: Int) = transaction {
            val proposalIds = GovVoteTable.slice(GovVoteTable.proposalId)
                .select { GovVoteTable.addressId eq addrId }
                .groupBy(GovVoteTable.proposalId)
                .orderBy(Pair(GovVoteTable.proposalId, SortOrder.DESC))
                .limit(limit, offset.toLong())
                .map { it[GovVoteTable.proposalId] }

            GovVoteTable.innerJoin(GovProposalTable, { GovVoteTable.proposalId }, { GovProposalTable.proposalId })
                .select { GovVoteTable.addressId eq addrId }
                .andWhere { GovVoteTable.proposalId inList proposalIds }
                .orderBy(Pair(GovVoteTable.proposalId, SortOrder.DESC))
                .map {
                    VoteDbRecord(
                        it[GovVoteTable.address],
                        it[GovVoteTable.isValidator],
                        it[GovVoteTable.vote],
                        it[GovVoteTable.weight].toDouble(),
                        it[GovVoteTable.blockHeight],
                        it[GovVoteTable.txHash],
                        it[GovVoteTable.txTimestamp],
                        it[GovVoteTable.proposalId],
                        it[GovProposalTable.title],
                        it[GovProposalTable.status]
                    )
                }
        }

        fun getByAddrIdCount(addrId: Int) = transaction {
            GovVoteRecord.find { GovVoteTable.addressId eq addrId }.groupBy { GovVoteTable.proposalId }.count()
        }

        fun buildInsert(
            txInfo: TxData,
            votes: List<Gov.WeightedVoteOption>,
            addrInfo: GovAddrData,
            proposalId: Long
        ) = transaction {
            votes.map {
                listOf(
                    -1,
                    proposalId,
                    addrInfo.addrId,
                    addrInfo.addr,
                    addrInfo.isValidator,
                    it.option.name,
                    txInfo.blockHeight,
                    txInfo.txHash,
                    txInfo.txTimestamp,
                    0,
                    it.weight.toDouble()
                ).toProcedureObject()
            }
        }
    }

    var proposalId by GovVoteTable.proposalId
    var addressId by GovVoteTable.addressId
    var address by GovVoteTable.address
    var isValidator by GovVoteTable.isValidator
    var vote by GovVoteTable.vote
    var weight by GovVoteTable.weight
    var blockHeight by GovVoteTable.blockHeight
    var txHash by GovVoteTable.txHash
    var txTimestamp by GovVoteTable.txTimestamp
    var txHashId by TxCacheRecord referencedOn GovVoteTable.txHashId
}

object GovDepositTable : IntIdTable(name = "gov_deposit") {
    val proposalId = long("proposal_id")
    val addressId = integer("address_id")
    val address = varchar("address", 128)
    val isValidator = bool("is_validator").default(false)
    val depositType = varchar("deposit_type", 128)
    val amount = decimal("amount", 100, 10)
    val denom = varchar("denom", 256)
    val blockHeight = integer("block_height")
    val txHash = varchar("tx_hash", 64)
    val txTimestamp = datetime("tx_timestamp")
    val txHashId = reference("tx_hash_id", TxCacheTable)
}

enum class DepositType { DEPOSIT, INITIAL_DEPOSIT }

class GovDepositRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<GovDepositRecord>(GovDepositTable) {

        fun findByProposalId(proposalId: Long) = transaction {
            GovDepositRecord.find { GovDepositTable.proposalId eq proposalId }.toMutableList()
        }

        fun findByTxHash(txHash: String) = transaction {
            GovDepositRecord.find { GovDepositTable.txHash eq txHash }.firstOrNull()
        }

        fun getByProposalIdPaginated(proposalId: Long, limit: Int, offset: Int) = transaction {
            GovDepositRecord.find { GovDepositTable.proposalId eq proposalId }
                .orderBy(Pair(GovDepositTable.blockHeight, SortOrder.DESC))
                .limit(limit, offset.toLong())
                .toList()
        }

        fun getByProposalIdCount(proposalId: Long) = transaction {
            GovDepositRecord.find { GovDepositTable.proposalId eq proposalId }.count()
        }

        fun buildInserts(
            txInfo: TxData,
            proposalId: Long,
            depositType: DepositType,
            amountList: List<CoinOuterClass.Coin>,
            addrInfo: GovAddrData
        ) = amountList.map { buildInsert(txInfo, proposalId, depositType, it, addrInfo) }

        fun buildInsert(
            txInfo: TxData,
            proposalId: Long,
            depositType: DepositType,
            amount: CoinOuterClass.Coin,
            addrInfo: GovAddrData
        ) = listOf(
            -1,
            proposalId,
            addrInfo.addrId,
            addrInfo.addr,
            addrInfo.isValidator,
            depositType.name,
            amount.amount.toBigDecimal(),
            amount.denom,
            txInfo.blockHeight,
            txInfo.txHash,
            txInfo.txTimestamp,
            0
        ).toProcedureObject()
    }

    var proposalId by GovDepositTable.proposalId
    var addressId by GovDepositTable.addressId
    var address by GovDepositTable.address
    var isValidator by GovDepositTable.isValidator
    var depositType by GovDepositTable.depositType
    var amount by GovDepositTable.amount
    var denom by GovDepositTable.denom
    var blockHeight by GovDepositTable.blockHeight
    var txHash by GovDepositTable.txHash
    var txTimestamp by GovDepositTable.txTimestamp
    var txHashId by TxCacheRecord referencedOn GovDepositTable.txHashId
}

object ProposalMonitorTable : IntIdTable(name = "proposal_monitor") {
    val proposalId = long("proposal_id")
    val submittedHeight = integer("submitted_height")
    val proposedCompletionHeight = integer("proposed_completion_height")
    val votingEndTime = datetime("voting_end_time")
    val proposalType = varchar("proposal_type", 256)
    val dataHash = varchar("matching_data_hash", 256)
    val readyForProcessing = bool("ready_for_processing").default(false)
    val processed = bool("processed").default(false)
}

class ProposalMonitorRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ProposalMonitorRecord>(ProposalMonitorTable) {

        fun buildInsert(
            proposalId: Long,
            submittedHeight: Int,
            proposedCompletionHeight: Int,
            votingEndTime: DateTime,
            proposalType: ProposalType,
            dataHash: String
        ) = listOf(
            -1,
            proposalId,
            submittedHeight,
            proposedCompletionHeight,
            votingEndTime,
            proposalType.name,
            dataHash,
            false,
            false
        ).toProcedureObject()

        fun ProposalMonitorRecord.checkIfProposalReadyForProcessing(
            proposalStatus: String,
            currentBlockTime: DateTime
        ) =
            if (proposalStatus == Gov.ProposalStatus.PROPOSAL_STATUS_PASSED.name &&
                this.votingEndTime.isBefore(currentBlockTime)
            )
                this.apply { this.readyForProcessing = true }
            else null

        fun getUnprocessed() = transaction {
            ProposalMonitorRecord
                .find { (ProposalMonitorTable.readyForProcessing eq false) and (ProposalMonitorTable.processed eq false) }
                .toList()
        }

        fun getReadyForProcessing() = transaction {
            ProposalMonitorRecord
                .find { (ProposalMonitorTable.readyForProcessing eq true) and (ProposalMonitorTable.processed eq false) }
                .toList()
        }
    }

    var proposalId by ProposalMonitorTable.proposalId
    var submittedHeight by ProposalMonitorTable.submittedHeight
    var proposedCompletionHeight by ProposalMonitorTable.proposedCompletionHeight
    var votingEndTime by ProposalMonitorTable.votingEndTime
    var proposalType by ProposalMonitorTable.proposalType
    var dataHash by ProposalMonitorTable.dataHash
    var readyForProcessing by ProposalMonitorTable.readyForProcessing
    var processed by ProposalMonitorTable.processed
}

enum class ProposalType { STORE_CODE }
