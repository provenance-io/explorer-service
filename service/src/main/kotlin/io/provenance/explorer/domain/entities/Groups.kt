package io.provenance.explorer.domain.entities

import cosmos.group.v1.Types
import cosmos.group.v1.Types.GroupInfo
import cosmos.group.v1.Types.Vote
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.core.sql.toProcedureObject
import io.provenance.explorer.domain.extensions.stringify
import io.provenance.explorer.domain.models.explorer.AddrData
import io.provenance.explorer.domain.models.explorer.GroupMembers
import io.provenance.explorer.domain.models.explorer.GroupsProposalData
import io.provenance.explorer.domain.models.explorer.GroupsProposalInsertData
import io.provenance.explorer.domain.models.explorer.TxData
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

object GroupsTable : IdTable<Int>(name = "groups") {
    override val id = integer("id").entityId()
    override val primaryKey = PrimaryKey(id)
    val adminAddress = varchar("admin_address", 128)
    val groupData = jsonb<GroupsTable, GroupInfo>("group_data", OBJECT_MAPPER)
    val groupMembers = jsonb<GroupsTable, GroupMembers>("group_members", OBJECT_MAPPER)
    val version = integer("ver")
    val blockHeight = integer("block_height")
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txHash = varchar("tx_hash", 64)
    val txTimestamp = datetime("tx_timestamp")
}

class GroupsRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<GroupsRecord>(GroupsTable) {

        fun buildInsert(
            group: GroupInfo,
            members: GroupMembers,
            txInfo: TxData
        ) =
            listOf(
                group.id,
                group.admin,
                group,
                members.stringify(),
                group.version,
                txInfo.blockHeight,
                -1,
                txInfo.txHash,
                txInfo.txTimestamp
            ).toProcedureObject()
    }

    var adminAddress by GroupsTable.adminAddress
    var groupData by GroupsTable.groupData
    var groupMembers by GroupsTable.groupMembers
    var version by GroupsTable.version
    var blockHeight by GroupsTable.blockHeight
    var txHashId by TxCacheRecord referencedOn GroupsTable.txHashId
    var txHash by GroupsTable.txHash
    var txTimestamp by GroupsTable.txTimestamp
}

object GroupsHistoryTable : IntIdTable(name = "groups_history") {
    val groupId = integer("group_id")
    val adminAddress = varchar("admin_address", 128)
    val groupData = jsonb<GroupsHistoryTable, GroupInfo>("group_data", OBJECT_MAPPER)
    val groupMembers = jsonb<GroupsHistoryTable, GroupMembers>("group_members", OBJECT_MAPPER)
    val version = integer("ver")
    val blockHeight = integer("block_height")
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txHash = varchar("tx_hash", 64)
    val txTimestamp = datetime("tx_timestamp")
}

class GroupsHistoryRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<GroupsHistoryRecord>(GroupsHistoryTable) {

        fun getByIdAndVersion(groupId: Int, version: Int) = transaction {
            GroupsHistoryRecord.find {
                (GroupsHistoryTable.groupId eq groupId) and (GroupsHistoryTable.version eq version)
            }.firstOrNull()
        }
    }

    var groupId by GroupsHistoryTable.groupId
    var adminAddress by GroupsHistoryTable.adminAddress
    var groupData by GroupsHistoryTable.groupData
    var groupMembers by GroupsHistoryTable.groupMembers
    var version by GroupsHistoryTable.version
    var blockHeight by GroupsHistoryTable.blockHeight
    var txHash by GroupsHistoryTable.txHash
    var txTimestamp by GroupsHistoryTable.txTimestamp
    var txHashId by TxCacheRecord referencedOn GroupsHistoryTable.txHashId
}

object GroupsPolicyTable : IntIdTable(name = "groups_policy") {
    val groupId = integer("groups_id")
    val policyAddress = varchar("policy_address", 128)
    val adminAddress = varchar("admin_address", 128)
    val policyData = jsonb<GroupsPolicyTable, Types.GroupPolicyInfo>("policy_data", OBJECT_MAPPER)
    val version = integer("ver")
    val blockHeight = integer("block_height")
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txHash = varchar("tx_hash", 64)
    val txTimestamp = datetime("tx_timestamp")
}

class GroupsPolicyRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<GroupsPolicyRecord>(GroupsPolicyTable) {

        fun findByPolicyAddr(policyAddr: String) = transaction {
            GroupsPolicyRecord.find { GroupsPolicyTable.policyAddress eq policyAddr }.firstOrNull()
        }

        fun buildInsert(
            policy: Types.GroupPolicyInfo,
            txInfo: TxData
        ) = transaction {
            listOf(
                -1,
                policy.groupId,
                policy.address,
                policy.admin,
                policy,
                policy.version,
                txInfo.blockHeight,
                -1,
                txInfo.txHash,
                txInfo.txTimestamp
            ).toProcedureObject()
        }
    }

    var groupId by GroupsPolicyTable.groupId
    var policyAddress by GroupsPolicyTable.policyAddress
    var adminAddress by GroupsPolicyTable.adminAddress
    var policyData by GroupsPolicyTable.policyData
    var version by GroupsPolicyTable.version
    var blockHeight by GroupsPolicyTable.blockHeight
    var txHash by GroupsPolicyTable.txHash
    var txTimestamp by GroupsPolicyTable.txTimestamp
    var txHashId by TxCacheRecord referencedOn GroupsPolicyTable.txHashId
}

object GroupsPolicyHistoryTable : IntIdTable(name = "groups_policy_history") {
    val groupId = integer("groups_id")
    val policyAddress = varchar("policy_address", 128)
    val adminAddress = varchar("admin_address", 128)
    val policyData = jsonb<GroupsPolicyHistoryTable, Types.GroupPolicyInfo>("policy_data", OBJECT_MAPPER)
    val version = integer("ver")
    val blockHeight = integer("block_height")
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txHash = varchar("tx_hash", 64)
    val txTimestamp = datetime("tx_timestamp")
}

class GroupsPolicyHistoryRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<GroupsPolicyHistoryRecord>(GroupsPolicyHistoryTable) {
    }

    var groupId by GroupsPolicyHistoryTable.groupId
    var policyAddress by GroupsPolicyHistoryTable.policyAddress
    var adminAddress by GroupsPolicyHistoryTable.adminAddress
    var policyData by GroupsPolicyHistoryTable.policyData
    var version by GroupsPolicyHistoryTable.version
    var blockHeight by GroupsPolicyHistoryTable.blockHeight
    var txHash by GroupsPolicyHistoryTable.txHash
    var txTimestamp by GroupsPolicyHistoryTable.txTimestamp
    var txHashId by TxCacheRecord referencedOn GroupsPolicyHistoryTable.txHashId
}

object GroupsProposalTable : IntIdTable(name = "groups_proposal") {
    val groupId = integer("groups_id")
    val policyAddressId = integer("policy_address_id")
    val policyAddress = varchar("policy_address", 128)
    val proposalId = integer("proposal_id")
    val proposalData = jsonb<GroupsProposalTable, GroupsProposalData>("proposal_data", OBJECT_MAPPER)
    val proposalNodeData = jsonb<GroupsProposalTable, Types.Proposal>("proposal_node_data", OBJECT_MAPPER).nullable()
    val proposalStatus = varchar("proposal_status", 128)
    val executorResult = varchar("executor_result", 128)
    val blockHeight = integer("block_height")
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txHash = varchar("tx_hash", 64)
    val txTimestamp = datetime("tx_timestamp")
}

class GroupsProposalRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<GroupsProposalRecord>(GroupsProposalTable) {

        fun getByUniqueKey(groupId: Int, policyAddr: String, proposalId: Int) = transaction {
            GroupsProposalRecord.find {
                (GroupsProposalTable.groupId eq groupId) and (GroupsProposalTable.policyAddress eq policyAddr) and
                    (GroupsProposalTable.proposalId eq proposalId)
            }.firstOrNull()
        }

        fun getById(proposalId: Long) = transaction {
            GroupsProposalRecord.find { GroupsProposalTable.proposalId eq proposalId.toInt() }
        }.firstOrNull()

        fun buildInsert(
            proposalData: GroupsProposalInsertData,
            txInfo: TxData
        ) = transaction {
            val policyId = GroupsPolicyRecord.findByPolicyAddr(proposalData.policyAddress)?.id?.value
                ?: throw IllegalStateException("Policy not found for address: ${proposalData.policyAddress}")
            listOf(
                -1,
                proposalData.groupId,
                policyId,
                proposalData.policyAddress,
                proposalData.proposalId,
                proposalData.data.stringify(),
                proposalData.nodeData,
                proposalData.status.name,
                proposalData.result.name,
                txInfo.blockHeight,
                -1,
                txInfo.txHash,
                txInfo.txTimestamp
            ).toProcedureObject()
        }
    }

    var groupId by GroupsProposalTable.groupId
    var policyAddressId by GroupsProposalTable.policyAddressId
    var policyAddress by GroupsProposalTable.policyAddress
    var proposalId by GroupsProposalTable.proposalId
    var proposalData by GroupsProposalTable.proposalData
    var proposalNodeData by GroupsProposalTable.proposalNodeData
    var proposalStatus by GroupsProposalTable.proposalStatus
    var executorResult by GroupsProposalTable.executorResult
    var blockHeight by GroupsProposalTable.blockHeight
    var txHash by GroupsProposalTable.txHash
    var txTimestamp by GroupsProposalTable.txTimestamp
    var txHashId by TxCacheRecord referencedOn GroupsProposalTable.txHashId
}

object GroupsVoteTable : IntIdTable(name = "groups_vote") {
    val proposalId = integer("proposal_id")
    val addressId = integer("address_id")
    val address = varchar("address", 128)
    val isValidator = bool("is_validator").default(false)
    val vote = varchar("vote", 128)
    val metadata = text("metadata")
    val weight = decimal("weight", 100, 50)
    val blockHeight = integer("block_height")
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txHash = varchar("tx_hash", 64)
    val txTimestamp = datetime("tx_timestamp")
}

class GroupsVoteRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<GroupsVoteRecord>(GroupsVoteTable) {

        fun buildInsert(
            address: AddrData,
            vote: Vote,
            voterWeight: BigDecimal,
            txInfo: TxData
        ) = transaction {
            listOf(
                -1,
                vote.proposalId,
                address.addrId,
                address.addr,
                address.isValidator,
                vote.option.name,
                vote.metadata,
                voterWeight,
                txInfo.blockHeight,
                -1,
                txInfo.txHash,
                txInfo.txTimestamp
            ).toProcedureObject()
        }
    }

    var proposalId by GroupsVoteTable.proposalId
    var addressId by GroupsVoteTable.addressId
    var address by GroupsVoteTable.address
    var isValidator by GroupsVoteTable.isValidator
    var vote by GroupsVoteTable.vote
    var metadata by GroupsVoteTable.metadata
    var weight by GroupsVoteTable.weight
    var blockHeight by GroupsVoteTable.blockHeight
    var txHash by GroupsVoteTable.txHash
    var txTimestamp by GroupsVoteTable.txTimestamp
    var txHashId by TxCacheRecord referencedOn GroupsVoteTable.txHashId
}
