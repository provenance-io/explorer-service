package io.provenance.explorer.domain.entities

import com.google.protobuf.Any
import cosmos.base.abci.v1beta1.Abci
import ibc.core.channel.v1.QueryOuterClass
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.sql.DEFAULT_DATE_TIME_STRING_FORMATTER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.core.sql.toProcedureObject
import io.provenance.explorer.domain.extensions.execAndMap
import io.provenance.explorer.domain.extensions.toDateTimeWithFormat
import io.provenance.explorer.domain.extensions.toDbHash
import io.provenance.explorer.domain.models.explorer.LedgerBySliceRes
import io.provenance.explorer.domain.models.explorer.LedgerInfo
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.grpc.extensions.toTendermintClientState
import io.provenance.explorer.model.IbcRelayer
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.DecimalColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.Max
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Sum
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.javatime.JavaLocalDateTimeColumnType
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet

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

        fun findByChain(chain: String) = transaction {
            IbcChannelRecord.find { IbcChannelTable.dstChainName eq chain }.toMutableList()
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

enum class IbcMovementType { IN, OUT }

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
    val sequence = integer("sequence")
    val uniqueHash = varchar("unique_hash", 256)
}

class IbcLedgerRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<IbcLedgerRecord>(IbcLedgerTable) {

        fun findMatchingRecord(ledger: LedgerInfo, txHash: String): IbcLedgerRecord? = transaction {
            IbcLedgerTable.select { IbcLedgerTable.channelId eq ledger.channel!!.id.value }
                .andWhere { IbcLedgerTable.sequence eq ledger.sequence }
                .andWhere { IbcLedgerTable.txHash neq txHash }
                .andWhere { if (ledger.movementIn) IbcLedgerTable.balanceIn.isNotNull() else IbcLedgerTable.balanceOut.isNotNull() }
                .orderBy(Pair(IbcLedgerTable.blockHeight, SortOrder.ASC))
                .firstOrNull()
                ?.let { IbcLedgerRecord.wrapRow(it) }
        }

        fun buildInsert(ledger: LedgerInfo, txData: TxData, match: IbcLedgerRecord?, log: Abci.ABCIMessageLog?) = transaction {
            listOf(
                0,
                match?.dstChainName ?: ledger.channel!!.dstChainName,
                match?.channelId ?: ledger.channel!!.id.value,
                match?.denom ?: ledger.denom,
                match?.denomTrace ?: ledger.denomTrace,
                match?.balanceIn ?: ledger.balanceIn?.toBigDecimal(),
                match?.balanceOut ?: ledger.balanceOut?.toBigDecimal(),
                match?.fromAddress ?: ledger.fromAddress,
                match?.toAddress ?: ledger.toAddress,
                match?.passThroughAddrId ?: ledger.passThroughAddress!!.id.value,
                match?.passThroughAddr ?: ledger.passThroughAddress!!.accountAddress,
                match?.logs ?: log,
                match?.blockHeight ?: txData.blockHeight,
                match?.txHashId ?: -1,
                match?.txHash ?: txData.txHash,
                match?.txTimestamp ?: txData.txTimestamp,
                if (match != null) {
                    if (!match.acknowledged) {
                        ledger.ack
                    } else {
                        match.acknowledged
                    }
                } else {
                    ledger.ack
                },
                if (match != null) {
                    if (!match.ackSuccess) {
                        ledger.ackSuccess
                    } else {
                        match.ackSuccess
                    }
                } else {
                    ledger.ackSuccess
                },
                match?.sequence ?: ledger.sequence,
                match?.uniqueHash ?: getUniqueHash(ledger)
            ).toProcedureObject()
        }

        fun getUniqueHash(ledger: LedgerInfo) = listOf(
            ledger.channel!!.id.value,
            ledger.sequence,
            if (ledger.movementIn) IbcMovementType.IN.name else IbcMovementType.OUT.name
        ).joinToString("").toDbHash()

        val lastTxTime = Max(IbcLedgerTable.txTimestamp, JavaLocalDateTimeColumnType())
        val balanceInSum = Sum(IbcLedgerTable.balanceIn, DecimalColumnType(100, 10))
        val balanceOutSum = Sum(IbcLedgerTable.balanceOut, DecimalColumnType(100, 10))

        fun getByChannel(srcPort: String?, srcChannel: String?) = transaction {
            val query = IbcLedgerTable.innerJoin(IbcChannelTable, { IbcLedgerTable.channelId }, { IbcChannelTable.id })
                .slice(
                    IbcLedgerTable.dstChainName, IbcChannelTable.srcPort, IbcChannelTable.srcChannel,
                    IbcChannelTable.dstPort, IbcChannelTable.dstChannel, IbcLedgerTable.denom,
                    IbcLedgerTable.denomTrace, balanceInSum, balanceOutSum, lastTxTime
                )
                .select { IbcLedgerTable.acknowledged and IbcLedgerTable.ackSuccess }

            if (srcPort != null && srcChannel != null) {
                query.andWhere { (IbcChannelTable.srcPort eq srcPort) and (IbcChannelTable.srcChannel eq srcChannel) }
            }

            query.groupBy(
                IbcLedgerTable.dstChainName,
                IbcChannelTable.srcPort,
                IbcChannelTable.srcChannel,
                IbcChannelTable.dstPort,
                IbcChannelTable.dstChannel,
                IbcLedgerTable.denom,
                IbcLedgerTable.denomTrace
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
                        it[IbcLedgerTable.denomTrace],
                        it[balanceInSum],
                        it[balanceOutSum],
                        it[lastTxTime]!!
                    )
                }
        }

        fun getByChain() = transaction {
            IbcLedgerTable
                .slice(
                    IbcLedgerTable.dstChainName,
                    IbcLedgerTable.denom,
                    IbcLedgerTable.denomTrace,
                    balanceInSum,
                    balanceOutSum,
                    lastTxTime
                )
                .select { IbcLedgerTable.acknowledged and IbcLedgerTable.ackSuccess }
                .groupBy(IbcLedgerTable.dstChainName, IbcLedgerTable.denom, IbcLedgerTable.denomTrace)
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
                        it[IbcLedgerTable.denomTrace],
                        it[balanceInSum],
                        it[balanceOutSum],
                        it[lastTxTime]!!
                    )
                }
        }

        fun getByDenom() = transaction {
            IbcLedgerTable
                .slice(IbcLedgerTable.denom, IbcLedgerTable.denomTrace, balanceInSum, balanceOutSum, lastTxTime)
                .select { IbcLedgerTable.acknowledged and IbcLedgerTable.ackSuccess }
                .groupBy(IbcLedgerTable.denom, IbcLedgerTable.denomTrace)
                .orderBy(Pair(IbcLedgerTable.denom, SortOrder.ASC))
                .map {
                    LedgerBySliceRes(
                        null,
                        null,
                        null,
                        null,
                        null,
                        it[IbcLedgerTable.denom],
                        it[IbcLedgerTable.denomTrace],
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
    var sequence by IbcLedgerTable.sequence
    var uniqueHash by IbcLedgerTable.uniqueHash
}

enum class IbcAckType { ACKNOWLEDGEMENT, RECEIVE, TIMEOUT, TRANSFER }

object IbcLedgerAckTable : IntIdTable(name = "ibc_ledger_ack") {
    val ledgerId = integer("ibc_ledger_id")
    val ackType = varchar("ack_type", 64)
    val blockHeight = integer("block_height")
    val txHashId = integer("tx_hash_id")
    val txHash = varchar("tx_hash", 64)
    val txTimestamp = datetime("tx_timestamp")
    val logs = jsonb<IbcLedgerAckTable, Abci.ABCIMessageLog>("logs", OBJECT_MAPPER)
    val changesEffected = bool("changes_effected").default(false)
}

class IbcLedgerAckRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<IbcLedgerAckRecord>(IbcLedgerAckTable) {

        fun buildInsert(ledger: LedgerInfo, txInfo: TxData, ledgerId: Int) =
            listOf(
                0,
                ledgerId,
                ledger.ackType!!.name,
                txInfo.blockHeight,
                -1,
                txInfo.txHash,
                txInfo.txTimestamp,
                ledger.logs,
                ledger.changesEffected
            ).toProcedureObject()
    }

    var ledgerId by IbcLedgerAckTable.ledgerId
    var ackType by IbcLedgerAckTable.ackType
    var blockHeight by IbcLedgerAckTable.blockHeight
    var txHashId by IbcLedgerAckTable.txHashId
    var txHash by IbcLedgerAckTable.txHash
    var txTimestamp by IbcLedgerAckTable.txTimestamp
    var logs by IbcLedgerAckTable.logs
    var changesEffected by IbcLedgerAckTable.changesEffected
}

object IbcRelayerTable : IntIdTable(name = "ibc_relayer") {
    val client = varchar("client", 128)
    val channelId = integer("channel_id").nullable()
    val addressId = integer("address_id")
    val address = varchar("address", 128)
}

class IbcRelayerRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<IbcRelayerRecord>(IbcRelayerTable) {

        fun insertIgnore(client: String, channelId: Int?, account: AccountRecord) =
            transaction {
                IbcRelayerTable.insertIgnore {
                    it[this.client] = client
                    it[this.channelId] = channelId
                    it[this.addressId] = account.id.value
                    it[this.address] = account.accountAddress
                }
            }

        fun getRelayersForChannel(channelId: Int) = transaction {
            val query = """
                SELECT
                       ir.channel_id,
                       ir.address AS address,
                       COUNT(DISTINCT sj2.join_key) AS tx_count,
                       MAX(tc.tx_timestamp) AS tx_timestamp
                FROM ibc_relayer ir
                JOIN signature_join sj ON ir.address = sj.join_key AND sj.join_type = 'ACCOUNT'
                JOIN signature_join sj2 ON sj.signature_id = sj2.signature_id AND sj2.join_type = 'TRANSACTION'
                JOIN tx_cache tc ON sj2.join_key = tc.hash
                WHERE ir.channel_id = ?
                GROUP BY ir.channel_id, ir.address;
            """.trimIndent()
            val arguments = mutableListOf<Pair<ColumnType, *>>(Pair(IntegerColumnType(), channelId))

            query.execAndMap(arguments) { it.toIbcRelayerObject() }
        }
    }

    var client by IbcRelayerTable.client
    var channelId by IbcRelayerTable.channelId
    var addressId by IbcRelayerTable.addressId
    var address by IbcRelayerTable.address
}

fun ResultSet.toIbcRelayerObject() = IbcRelayer(
    this.getString("address"),
    this.getLong("tx_count"),
    this.getString("tx_timestamp").toDateTimeWithFormat(DEFAULT_DATE_TIME_STRING_FORMATTER)
)
