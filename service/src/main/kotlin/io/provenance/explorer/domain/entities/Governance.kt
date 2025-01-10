package io.provenance.explorer.domain.entities

import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.util.JsonFormat
import cosmos.base.v1beta1.CoinOuterClass
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.Array
import io.provenance.explorer.domain.core.sql.ArrayAgg
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.core.sql.toProcedureObject
import io.provenance.explorer.domain.extensions.stringify
import io.provenance.explorer.domain.extensions.toDecimalNew
import io.provenance.explorer.domain.extensions.toObjectNodeList
import io.provenance.explorer.domain.models.explorer.AddrData
import io.provenance.explorer.domain.models.explorer.GovContentV1List
import io.provenance.explorer.domain.models.explorer.ProposalParamHeights
import io.provenance.explorer.domain.models.explorer.ProposalTimingData
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.VoteDbRecord
import io.provenance.explorer.domain.models.explorer.VoteDbRecordAgg
import io.provenance.explorer.domain.models.explorer.VoteWeightDbObj
import io.provenance.explorer.service.getProposalTypeLegacy
import io.provenance.explorer.service.getProposalTypeList
import io.provenance.explorer.service.toProposalContent
import io.provenance.explorer.service.toProposalTitleAndDescription
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.castTo
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import cosmos.gov.v1.Gov as GovV1
import cosmos.gov.v1beta1.Gov as GovV1beta1

object GovProposalTable : IntIdTable(name = "gov_proposal") {
    val proposalId = long("proposal_id")
    val proposalType = text("proposal_type")
    val addressId = integer("address_id")
    val address = varchar("address", 128)
    val isValidator = bool("is_validator").default(false)
    val title = text("title")
    val description = text("description")
    val status = varchar("status", 128)
    val dataV1beta1 = jsonb<GovProposalTable, GovV1beta1.Proposal>("data_v1beta1", OBJECT_MAPPER).nullable()
    val contentV1beta1 = jsonb<GovProposalTable, ObjectNode>("content_v1beta1", OBJECT_MAPPER).nullable()
    val blockHeight = integer("block_height")
    val txHash = varchar("tx_hash", 64)
    val txTimestamp = datetime("tx_timestamp")
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val depositParamCheckHeight = integer("deposit_param_check_height")
    val votingParamCheckHeight = integer("voting_param_check_height")
    val dataV1 = jsonb<GovProposalTable, GovV1.Proposal>("data_v1", OBJECT_MAPPER).nullable()
    val contentV1 = jsonb<GovProposalTable, GovContentV1List>("content_v1", OBJECT_MAPPER).nullable()
}

val passStatuses = listOf(GovV1.ProposalStatus.PROPOSAL_STATUS_PASSED.name)
val failStatuses =
    listOf(GovV1.ProposalStatus.PROPOSAL_STATUS_FAILED.name, GovV1.ProposalStatus.PROPOSAL_STATUS_REJECTED.name)
val completeStatuses = passStatuses + failStatuses

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
            GovProposalRecord.find { GovProposalTable.proposalType like "%${type.getProposalTypeLegacy()}%" }
                .orderBy(Pair(GovProposalTable.proposalId, SortOrder.ASC))
                .toList()
        }

        fun getNonFinalProposals() = transaction {
            GovProposalRecord.find { GovProposalTable.status notInList completeStatuses }.toList()
        }

        fun getCompletedProposalsForPeriod(startDate: DateTime, endDate: DateTime) = transaction {
            GovProposalRecord.find {
                (GovProposalTable.txTimestamp.between(startDate, endDate)) and
                    (GovProposalTable.status inList completeStatuses) and
                    (GovProposalTable.votingParamCheckHeight neq -1)
            }.toMutableList()
        }

        fun buildInsert(
            proposal: GovV1.Proposal,
            protoPrinter: JsonFormat.Printer,
            txInfo: TxData,
            addrInfo: AddrData,
            isSubmit: Boolean,
            paramHeights: ProposalParamHeights
        ) = transaction {
            proposal.toProposalTitleAndDescription(protoPrinter).let { (title, description) ->
                val (hash, block, time) = findByProposalId(proposal.id)?.let {
                    if (isSubmit) {
                        Triple(txInfo.txHash, txInfo.blockHeight, txInfo.txTimestamp)
                    } else {
                        Triple(it.txHash, it.blockHeight, it.txTimestamp)
                    }
                } ?: Triple(txInfo.txHash, txInfo.blockHeight, txInfo.txTimestamp)
                listOf(
                    -1,
                    proposal.id,
                    proposal.messagesList.getProposalTypeList(),
                    addrInfo.addrId,
                    addrInfo.addr,
                    addrInfo.isValidator,
                    title,
                    description,
                    proposal.status.name,
                    null,
                    null,
                    block,
                    hash,
                    time,
                    0,
                    paramHeights.depositCheckHeight,
                    paramHeights.votingCheckHeight,
                    proposal,
                    proposal.toProposalContent().stringify()
                ).toProcedureObject()
            }
        }
    }

    fun getContentToPrint(printer: JsonFormat.Printer) =
        contentV1?.list?.toObjectNodeList(printer) ?: listOf(contentV1beta1!!)

    fun getDataTimings() =
        dataV1?.let { ProposalTimingData(it.submitTime, it.depositEndTime, it.votingStartTime, it.votingEndTime) }
            ?: dataV1beta1!!.let { ProposalTimingData(it.submitTime, it.depositEndTime, it.votingStartTime, it.votingEndTime) }

    var proposalId by GovProposalTable.proposalId
    var proposalType by GovProposalTable.proposalType
    var addressId by GovProposalTable.addressId
    var address by GovProposalTable.address
    var isValidator by GovProposalTable.isValidator
    var title by GovProposalTable.title
    var description by GovProposalTable.description
    var status by GovProposalTable.status
    var dataV1beta1 by GovProposalTable.dataV1beta1
    var contentV1beta1 by GovProposalTable.contentV1beta1
    var blockHeight by GovProposalTable.blockHeight
    var txHash by GovProposalTable.txHash
    var txTimestamp by GovProposalTable.txTimestamp
    var txHashId by TxCacheRecord referencedOn GovProposalTable.txHashId
    var depositParamCheckHeight by GovProposalTable.depositParamCheckHeight
    var votingParamCheckHeight by GovProposalTable.votingParamCheckHeight
    var dataV1 by GovProposalTable.dataV1
    var contentV1 by GovProposalTable.contentV1
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
    val justification = text("justification").nullable()
}

fun kotlin.Array<kotlin.Array<String>>.toVoteWeightObj() =
    this.map { VoteWeightDbObj(it[0], it[1].toDouble()) }.toList()

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
                        "",
                        it.justification
                    )
                }
        }

        fun findByProposalIdPaginated(proposalId: Long, limit: Int, offset: Int) = transaction {
            val array =
                Array<String>(TextColumnType(), GovVoteTable.vote.castTo(TextColumnType()), GovVoteTable.weight.castTo(TextColumnType()))
            val arrayAgg = ArrayAgg(array)
            GovVoteTable.slice(
                listOf(
                    GovVoteTable.proposalId,
                    GovVoteTable.address,
                    GovVoteTable.isValidator,
                    arrayAgg,
                    GovVoteTable.blockHeight,
                    GovVoteTable.txHash,
                    GovVoteTable.txTimestamp,
                    GovVoteTable.justification
                )
            ).select { GovVoteTable.proposalId eq proposalId }
                .groupBy(
                    GovVoteTable.proposalId,
                    GovVoteTable.address,
                    GovVoteTable.isValidator,
                    GovVoteTable.blockHeight,
                    GovVoteTable.txHash,
                    GovVoteTable.txTimestamp,
                    GovVoteTable.justification
                )
                .orderBy(Pair(GovVoteTable.blockHeight, SortOrder.DESC))
                .limit(limit, offset.toLong())
                .map {
                    VoteDbRecordAgg(
                        it[GovVoteTable.address],
                        it[GovVoteTable.isValidator],
                        it[arrayAgg].toVoteWeightObj(),
                        it[GovVoteTable.blockHeight],
                        it[GovVoteTable.txHash],
                        it[GovVoteTable.txTimestamp],
                        it[GovVoteTable.proposalId],
                        "",
                        "",
                        it[GovVoteTable.justification]
                    )
                }
        }

        fun findByProposalIdCount(proposalId: Long) = transaction {
            val array =
                Array<String>(TextColumnType(), GovVoteTable.vote.castTo(TextColumnType()), GovVoteTable.weight.castTo(TextColumnType()))
            val arrayAgg = ArrayAgg(array)
            GovVoteTable.slice(listOf(GovVoteTable.proposalId, GovVoteTable.address, arrayAgg))
                .select { GovVoteTable.proposalId eq proposalId }
                .groupBy(GovVoteTable.proposalId, GovVoteTable.address)
                .count()
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
                        it[GovProposalTable.status],
                        it[GovVoteTable.justification]
                    )
                }
        }

        fun getByAddrIdCount(addrId: Int) = transaction {
            GovVoteRecord.find { GovVoteTable.addressId eq addrId }.groupBy { GovVoteTable.proposalId }.count()
        }

        fun getAddressVotesForProposalList(addrId: Int, list: List<Long>) = transaction {
            GovVoteRecord.find { (GovVoteTable.proposalId inList list) and (GovVoteTable.addressId eq addrId) }
                .toMutableList()
        }

        fun buildInsert(
            txInfo: TxData,
            votes: List<GovV1.WeightedVoteOption>,
            addrInfo: AddrData,
            proposalId: Long,
            justification: String?
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
                    it.weight.toDecimalNew(),
                    justification
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
    val justification by GovVoteTable.justification
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
            addrInfo: AddrData
        ) = amountList.map { buildInsert(txInfo, proposalId, depositType, it, addrInfo) }

        fun buildInsert(
            txInfo: TxData,
            proposalId: Long,
            depositType: DepositType,
            amount: CoinOuterClass.Coin,
            addrInfo: AddrData
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
            proposalType: MonitorProposalType,
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
            if (passStatuses.contains(proposalStatus) && this.votingEndTime.isBefore(currentBlockTime)) {
                this.apply { this.readyForProcessing = true }
            } else if (failStatuses.contains(proposalStatus)) {
                this.apply {
                    this.readyForProcessing = true
                    this.processed = true
                }
            } else {
                null
            }

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

        fun getByProposalId(proposalId: Long) = transaction {
            ProposalMonitorRecord.find { ProposalMonitorTable.proposalId eq proposalId }.firstOrNull()
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

enum class MonitorProposalType { STORE_CODE }
