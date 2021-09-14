package io.provenance.explorer.domain.entities

import com.google.protobuf.Any
import cosmos.base.abci.v1beta1.Abci
import ibc.core.channel.v1.QueryOuterClass
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.models.explorer.LedgerBySliceRes
import io.provenance.explorer.domain.models.explorer.LedgerInfo
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.grpc.extensions.toLocalhostClientState
import io.provenance.explorer.grpc.extensions.toTendermintClientState
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.DecimalColumnType
import org.jetbrains.exposed.sql.Max
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Sum
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.jodatime.DateColumnType
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

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

object IbcLedgerTable : IntIdTable(name = "ibc_ledger") {
    val dstChainName = varchar("dst_chain_name", 256)
    val channelId = integer("channel_id")
    val denom = varchar("denom", 256)
    val denomTrace = text("denom_trace")
    val balanceIn = decimal("balance_in", 100, 10).nullable()
    val balanceOut = decimal("balance_out", 100, 10).nullable()
    val fromAddress = varchar("from_address", 256)
    val toAddress = varchar("to_address", 256)
    val passThroughAddrId = integer("pass_through_address_id")
    val passThroughAddr = varchar("pass_through_address", 128)
    val logs = jsonb<IbcLedgerTable, Abci.ABCIMessageLog>("logs", OBJECT_MAPPER)
    val blockHeight = integer("block_height")
    val txHashId = integer("tx_hash_id")
    val txHash = varchar("tx_hash", 64)
    val txTimestamp = datetime("tx_timestamp")
    val acknowledged = bool("acknowledged").default(false)
    val ackSuccess = bool("ack_success").default(false)
    val ackLogs = jsonb<IbcLedgerTable, Abci.ABCIMessageLog>("ack_logs", OBJECT_MAPPER).nullable()
    val ackBlockHeight = integer("ack_block_height").nullable()
    val ackTxHashId = integer("ack_tx_hash_id").nullable()
    val ackTxHash = varchar("ack_tx_hash", 64).nullable()
    val ackTxTimestamp = datetime("ack_tx_timestamp").nullable()
}

class IbcLedgerRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<IbcLedgerRecord>(IbcLedgerTable) {

        fun findMatchingRecord(ledger: LedgerInfo): IbcLedgerRecord? = transaction {
            if (ledger.ack) {
                IbcLedgerTable.select { IbcLedgerTable.channelId eq ledger.channel.id.value }
                    .andWhere { IbcLedgerTable.fromAddress eq ledger.fromAddress }
                    .andWhere { IbcLedgerTable.toAddress eq ledger.toAddress }
                    .andWhere { IbcLedgerTable.denomTrace eq ledger.denomTrace }
                    .andWhere { IbcLedgerTable.balanceIn eq ledger.balanceIn?.toBigDecimal() }
                    .andWhere { IbcLedgerTable.balanceOut eq ledger.balanceOut?.toBigDecimal() }
                    .orderBy(Pair(IbcLedgerTable.blockHeight, SortOrder.DESC))
                    .first()
                    .let { IbcLedgerRecord.wrapRow(it) }
            } else null
        }

        fun getOrInsert(
            ledger: LedgerInfo,
            txData: TxData
        ) = transaction {
            findMatchingRecord(ledger)?.apply {
                this.acknowledged = true
                this.ackSuccess = ledger.ack
                this.ackLogs = ledger.logs
                this.ackBlockHeight = txData.blockHeight
                this.ackTxHashId = txData.txHashId
                this.ackTxHash = txData.txHash
                this.ackTxTimestamp = txData.txTimestamp
            } ?: IbcLedgerTable.insertAndGetId {
                it[this.channelId] = ledger.channel.id.value
                it[this.dstChainName] = ledger.channel.dstChainName
                it[this.denom] = ledger.denom
                it[this.denomTrace] = ledger.denomTrace
                it[this.balanceIn] = ledger.balanceIn?.toBigDecimal()
                it[this.balanceOut] = ledger.balanceOut?.toBigDecimal()
                it[this.fromAddress] = ledger.fromAddress
                it[this.toAddress] = ledger.toAddress
                it[this.passThroughAddrId] = ledger.passThroughAddress!!.id.value
                it[this.passThroughAddr] = ledger.passThroughAddress!!.accountAddress
                it[this.logs] = ledger.logs
                it[this.blockHeight] = txData.blockHeight
                it[this.txHashId] = txData.txHashId!!
                it[this.txHash] = txData.txHash
                it[this.txTimestamp] = txData.txTimestamp
                if (ledger.balanceIn != null) {
                    it[this.acknowledged] = true
                    it[this.ackSuccess] = true
                }
            }.let { IbcLedgerRecord.findById(it)!! }
        }

        val lastTxTime = Max(IbcLedgerTable.txTimestamp, DateColumnType(true))
        val balanceInSum = Sum(IbcLedgerTable.balanceIn, DecimalColumnType(100, 10))
        val balanceOutSum = Sum(IbcLedgerTable.balanceOut, DecimalColumnType(100, 10))

        fun getByChannel() = transaction {
            IbcLedgerTable.innerJoin(IbcChannelTable, { IbcLedgerTable.channelId }, { IbcChannelTable.id })
                .slice(
                    IbcLedgerTable.dstChainName, IbcChannelTable.srcPort, IbcChannelTable.srcChannel,
                    IbcChannelTable.dstPort, IbcChannelTable.dstChannel, IbcLedgerTable.denom, balanceInSum,
                    balanceOutSum, lastTxTime
                )
                .select { IbcLedgerTable.acknowledged and IbcLedgerTable.ackSuccess }
                .groupBy(
                    IbcLedgerTable.dstChainName, IbcChannelTable.srcPort, IbcChannelTable.srcChannel,
                    IbcChannelTable.dstPort, IbcChannelTable.dstChannel, IbcLedgerTable.denom
                )
                .orderBy(
                    Pair(IbcLedgerTable.dstChainName, SortOrder.ASC),
                    Pair(IbcChannelTable.srcChannel, SortOrder.ASC),
                    Pair(IbcLedgerTable.denom, SortOrder.ASC)
                )
                .map {
                    LedgerBySliceRes(
                        it[IbcLedgerTable.dstChainName],
                        it[IbcChannelTable.srcPort],
                        it[IbcChannelTable.srcChannel],
                        it[IbcChannelTable.dstPort],
                        it[IbcChannelTable.dstChannel],
                        it[IbcLedgerTable.denom],
                        it[balanceInSum],
                        it[balanceOutSum],
                        it[lastTxTime]!!
                    )
                }
        }

        fun getByChain() = transaction {
            IbcLedgerTable
                .slice(IbcLedgerTable.dstChainName, IbcLedgerTable.denom, balanceInSum, balanceOutSum, lastTxTime)
                .select { IbcLedgerTable.acknowledged and IbcLedgerTable.ackSuccess }
                .groupBy(IbcLedgerTable.dstChainName, IbcLedgerTable.denom)
                .orderBy(
                    Pair(IbcLedgerTable.dstChainName, SortOrder.ASC),
                    Pair(IbcLedgerTable.denom, SortOrder.ASC)
                )
                .map {
                    LedgerBySliceRes(
                        it[IbcLedgerTable.dstChainName],
                        null,
                        null,
                        null,
                        null,
                        it[IbcLedgerTable.denom],
                        it[balanceInSum],
                        it[balanceOutSum],
                        it[lastTxTime]!!
                    )
                }
        }

        fun getByDenom() = transaction {
            IbcLedgerTable
                .slice(IbcLedgerTable.denom, balanceInSum, balanceOutSum, lastTxTime)
                .select { IbcLedgerTable.acknowledged and IbcLedgerTable.ackSuccess }
                .groupBy(IbcLedgerTable.denom)
                .orderBy(Pair(IbcLedgerTable.denom, SortOrder.ASC))
                .map {
                    LedgerBySliceRes(
                        null,
                        null,
                        null,
                        null,
                        null,
                        it[IbcLedgerTable.denom],
                        it[balanceInSum],
                        it[balanceOutSum],
                        it[lastTxTime]!!
                    )
                }
        }
    }

    var dstChainName by IbcLedgerTable.dstChainName
    var channelId by IbcLedgerTable.channelId
    var denom by IbcLedgerTable.denom
    var denomTrace by IbcLedgerTable.denomTrace
    var balanceIn by IbcLedgerTable.balanceIn
    var balanceOut by IbcLedgerTable.balanceOut
    var fromAddress by IbcLedgerTable.fromAddress
    var toAddress by IbcLedgerTable.toAddress
    var passThroughAddrId by IbcLedgerTable.passThroughAddrId
    var passThroughAddr by IbcLedgerTable.passThroughAddr
    var logs by IbcLedgerTable.logs
    var blockHeight by IbcLedgerTable.blockHeight
    var txHashId by IbcLedgerTable.txHashId
    var txHash by IbcLedgerTable.txHash
    var txTimestamp by IbcLedgerTable.txTimestamp
    var acknowledged by IbcLedgerTable.acknowledged
    var ackSuccess by IbcLedgerTable.ackSuccess
    var ackLogs by IbcLedgerTable.ackLogs
    var ackBlockHeight by IbcLedgerTable.ackBlockHeight
    var ackTxHashId by IbcLedgerTable.ackTxHashId
    var ackTxHash by IbcLedgerTable.ackTxHash
    var ackTxTimestamp by IbcLedgerTable.ackTxTimestamp
}
