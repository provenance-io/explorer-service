package io.provenance.explorer.domain.entities

import com.google.protobuf.util.JsonFormat
import cosmos.gov.v1beta1.Gov
import ibc.core.channel.v1.QueryOuterClass
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.models.explorer.GovAddrData
import io.provenance.explorer.domain.models.explorer.GovTxData
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import com.google.protobuf.Any
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.grpc.extensions.toLocalhostClientState
import io.provenance.explorer.grpc.extensions.toSoloMachineClientState
import io.provenance.explorer.grpc.extensions.toTendermintClientState
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll


object IbcChannelTable : IntIdTable(name = "ibc_channel") {
    val client = varchar("client", 128)
    val dstChainName = varchar("dst_chain_name", 256)
    val srcPort = varchar("src_port", 128)
    val srcChannel = varchar("src_channel", 128)
    val dstPort = varchar("dst_port", 128)
    val dstChannel = varchar("dst_channel", 128)
    val status = varchar("status", 64)
    val escrowAddrId = integer("escrow_address_id")
    val escrowAddr = varchar("escrow_address", 128)
    val data = jsonb<IbcChannelTable, QueryOuterClass.QueryChannelResponse>("data", OBJECT_MAPPER)
}

fun Any.getChainName() =
    when {
        typeUrl.contains("ibc.lightclients.tendermint.v1.ClientState") ->
            this.toTendermintClientState().chainId
        typeUrl.contains("ibc.lightclients.localhost.v1.ClientState") ->
            this.toLocalhostClientState().chainId
        typeUrl.contains("ibc.lightclients.solomachine.v1.ClientState") -> "Unknown"
        else -> throw ResourceNotFoundException("The Client State type is unknown: '$typeUrl'")
    }

class IbcChannelRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<IbcChannelRecord>(IbcChannelTable) {

        fun findBySrcPortSrcChannel(port: String, channel: String) = transaction {
            IbcChannelRecord.find { (IbcChannelTable.srcPort eq port) and (IbcChannelTable.srcChannel eq channel) }
                .firstOrNull()
        }

        fun getOrInsert(
            portId: String,
            channelId: String,
            channel: QueryOuterClass.QueryChannelResponse,
            client: QueryOuterClass.QueryChannelClientStateResponse,
            escrowAcc: AccountRecord
        ) = transaction {
                findBySrcPortSrcChannel(portId, channelId)?.apply {
                    this.status = channel.channel.state.name
                    this.data = channel
                } ?: IbcChannelTable.insertAndGetId {
                    it[this.client] = client.identifiedClientState.clientId
                    it[this.dstChainName] = client.identifiedClientState.clientState.getChainName()
                    it[this.srcPort] = portId
                    it[this.srcChannel] = channelId
                    it[this.dstPort] = channel.channel.counterparty.portId
                    it[this.dstChannel] = channel.channel.counterparty.channelId
                    it[this.status] = channel.channel.state.name
                    it[this.escrowAddrId] = escrowAcc.id.value
                    it[this.escrowAddr] = escrowAcc.accountAddress
                    it[this.data] = channel
                }.let { IbcChannelRecord.findById(it)!! }
            }

        fun getAll() = transaction { IbcChannelRecord.all().toMutableList() }

        fun findByStatus(status: String) = transaction {
            IbcChannelRecord.find { IbcChannelTable.status eq status }.toMutableList()
        }
    }

    var client by IbcChannelTable.client
    var dstChainName by IbcChannelTable.dstChainName
    var srcPort by IbcChannelTable.srcPort
    var srcChannel by IbcChannelTable.srcChannel
    var dstPort by IbcChannelTable.dstPort
    var dstChannel by IbcChannelTable.dstChannel
    var status by IbcChannelTable.status
    var escrowAddrId by IbcChannelTable.escrowAddrId
    var escrowAddr by IbcChannelTable.escrowAddr
    var data by IbcChannelTable.data
}
