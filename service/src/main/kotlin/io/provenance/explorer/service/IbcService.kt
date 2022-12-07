package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import cosmos.base.abci.v1beta1.Abci
import ibc.applications.transfer.v1.Transfer
import ibc.applications.transfer.v1.Tx.MsgTransfer
import ibc.core.channel.v1.ChannelOuterClass
import ibc.core.channel.v1.Tx.MsgAcknowledgement
import ibc.core.channel.v1.Tx.MsgRecvPacket
import ibc.core.channel.v1.Tx.MsgTimeout
import ibc.core.channel.v1.Tx.MsgTimeoutOnClose
import io.provenance.explorer.config.ExplorerProperties.Companion.PROV_ACC_PREFIX
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.IbcAckType
import io.provenance.explorer.domain.entities.IbcChannelRecord
import io.provenance.explorer.domain.entities.IbcLedgerAckRecord
import io.provenance.explorer.domain.entities.IbcLedgerRecord
import io.provenance.explorer.domain.entities.IbcRelayerRecord
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.TxMarkerJoinRecord
import io.provenance.explorer.domain.extensions.decodeHex
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.Balance
import io.provenance.explorer.domain.models.explorer.BalanceByChannel
import io.provenance.explorer.domain.models.explorer.BalancesByChain
import io.provenance.explorer.domain.models.explorer.BalancesByChannel
import io.provenance.explorer.domain.models.explorer.Channel
import io.provenance.explorer.domain.models.explorer.ChannelStatus
import io.provenance.explorer.domain.models.explorer.IbcChannelStatus
import io.provenance.explorer.domain.models.explorer.IbcDenomDetail
import io.provenance.explorer.domain.models.explorer.IbcDenomListed
import io.provenance.explorer.domain.models.explorer.LedgerInfo
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.grpc.extensions.denomEventRegexParse
import io.provenance.explorer.grpc.extensions.scrubQuotes
import io.provenance.explorer.grpc.v1.IbcGrpcClient
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class IbcService(
    private val ibcClient: IbcGrpcClient,
    private val assetService: AssetService,
    private val accountService: AccountService,
    private val protoPrinter: JsonFormat.Printer,
) {
    protected val logger = logger(IbcService::class)

    fun saveIbcChannel(port: String, channel: String) = runBlocking {
        val channelRes = async { ibcClient.getChannel(port, channel) }
        val client = async { ibcClient.getClientForChannel(port, channel) }
        val escrowAddr = async { ibcClient.getEscrowAddress(port, channel, PROV_ACC_PREFIX) }
        val escrowAccount = accountService.getAccountRaw(escrowAddr.await())
        IbcChannelRecord.getOrInsert(port, channel, channelRes.await(), client.await(), escrowAccount)
    }

    fun buildIbcLedger(ledger: LedgerInfo, txData: TxData, match: IbcLedgerRecord?) =
        IbcLedgerRecord.buildInsert(ledger, txData, match)

    fun buildIbcLedgerAck(ledger: LedgerInfo, txData: TxData, ledgerId: Int) =
        IbcLedgerAckRecord.buildInsert(ledger, txData, ledgerId)

    fun parseTransfer(msg: MsgTransfer, logs: Abci.ABCIMessageLog): LedgerInfo {
        val typed = logs.eventsList.associateBy { it.type }
        val channel = typed["send_packet"]!!.let { event ->
            val port = event.attributesList.first { it.key == "packet_src_port" }.value
            val channel = event.attributesList.first { it.key == "packet_src_channel" }.value
            IbcChannelRecord.findBySrcPortSrcChannel(port, channel)
        }
        val ledger = LedgerInfo(channel = channel!!, logs = logs)
        ledger.ackType = IbcAckType.TRANSFER
        typed.forEach { (k, v) ->
            when (k) {
                "transfer" -> v.attributesList.forEach {
                    when (it.key) {
                        "recipient" -> ledger.passThroughAddress = accountService.getAccountRaw(it.value)
                    }
                }
                "send_packet" -> v.attributesList.forEach {
                    when (it.key) {
                        "packet_data_hex" ->
                            if (ledger.denom.isEmpty())
                                it.value.decodeHex().toObjectNode().let { node ->
                                    ledger.denom = node.get("denom").asText()
                                    ledger.denomTrace =
                                        if (ledger.denom.startsWith("ibc")) getDenomTrace(ledger.denom.getIbcHash()).toFullPath() else ledger.denom
                                    ledger.toAddress = node.get("receiver").asText()
                                    ledger.fromAddress = node.get("sender").asText()
                                    ledger.balanceOut = node.get("amount").asText()
                                }
                        "packet_data" ->
                            if (ledger.denom.isEmpty())
                                it.value.toObjectNode().let { node ->
                                    ledger.denom = node.get("denom").asText()
                                    ledger.denomTrace =
                                        if (ledger.denom.startsWith("ibc")) getDenomTrace(ledger.denom.getIbcHash()).toFullPath() else ledger.denom
                                    ledger.toAddress = node.get("receiver").asText()
                                    ledger.fromAddress = node.get("sender").asText()
                                    ledger.balanceOut = node.get("amount").asText()
                                }
                        "packet_sequence" -> ledger.sequence = it.value.toInt()
                    }
                }
            }
        }
        return ledger
    }

    fun parseRecv(txSuccess: Boolean, msg: MsgRecvPacket, logs: Abci.ABCIMessageLog): LedgerInfo {
        val ledger = LedgerInfo()
        ledger.logs = logs
        ledger.ackType = IbcAckType.RECEIVE
        ledger.movementIn = true
        if (txSuccess) {
            ledger.ack = true
            val typed = logs.eventsList.associateBy { it.type }
            ledger.channel = typed["recv_packet"]!!.let { event ->
                val port = event.attributesList.first { it.key == "packet_dst_port" }.value
                val channel = event.attributesList.first { it.key == "packet_dst_channel" }.value
                IbcChannelRecord.findBySrcPortSrcChannel(port, channel)
            }
            typed.forEach { (k, v) ->
                when (k) {
                    "recv_packet" -> v.attributesList.forEach {
                        when (it.key) {
                            "packet_data_hex" ->
                                if (ledger.toAddress.isEmpty())
                                    it.value.decodeHex().toObjectNode().let { node ->
                                        ledger.toAddress = node.get("receiver").asText()
                                        ledger.fromAddress = node.get("sender").asText()
                                        ledger.balanceIn = node.get("amount").asText()
                                    }
                            "packet_data" ->
                                if (ledger.toAddress.isEmpty())
                                    it.value.toObjectNode().let { node ->
                                        ledger.toAddress = node.get("receiver").asText()
                                        ledger.fromAddress = node.get("sender").asText()
                                        ledger.balanceIn = node.get("amount").asText()
                                    }
                            "packet_sequence" -> ledger.sequence = it.value.toInt()
                        }
                    }
                    "transfer" ->
                        v.attributesList.forEach {
                            when (it.key) {
                                "sender" -> ledger.passThroughAddress = accountService.getAccountRaw(it.value)
                                "amount" -> {
                                    ledger.denom = it.value.scrubQuotes().denomEventRegexParse().first()
                                    ledger.denomTrace =
                                        if (ledger.denom.startsWith("ibc")) getDenomTrace(ledger.denom.getIbcHash()).toFullPath() else ledger.denom
                                }
                            }
                            ledger.changesEffected = true
                            ledger.ackSuccess = true
                        }
                }
            }
        } else {
            ledger.channel = IbcChannelRecord.findBySrcPortSrcChannel(
                msg.packet.destinationPort,
                msg.packet.destinationChannel
            )
            ledger.sequence = msg.packet.sequence.toInt()
        }
        return ledger
    }

    fun parseAcknowledge(txSuccess: Boolean, msg: MsgAcknowledgement, logs: Abci.ABCIMessageLog): LedgerInfo {
        val ledger = LedgerInfo()
        ledger.logs = logs
        ledger.ackType = IbcAckType.ACKNOWLEDGEMENT
        if (txSuccess) {
            ledger.ack = true
            val typed = logs.eventsList.associateBy { it.type }
            ledger.channel = typed["acknowledge_packet"]!!.let { event ->
                val port = event.attributesList.first { it.key == "packet_src_port" }.value
                val channel = event.attributesList.first { it.key == "packet_src_channel" }.value
                IbcChannelRecord.findBySrcPortSrcChannel(port, channel)
            }
            typed.forEach { (k, v) ->
                when (k) {
                    "acknowledge_packet" -> v.attributesList.forEach {
                        when (it.key) {
                            "packet_sequence" -> ledger.sequence = it.value.toInt()
                        }
                    }
                    "fungible_token_packet" -> v.attributesList.firstOrNull { it.key == "success" }
                        ?.let {
                            ledger.changesEffected = true
                            ledger.ackSuccess = true
                        }
                }
            }
        } else {
            ledger.channel = IbcChannelRecord.findBySrcPortSrcChannel(
                msg.packet.sourcePort,
                msg.packet.sourceChannel
            )
            ledger.sequence = msg.packet.sequence.toInt()
        }
        return ledger
    }

    fun parseTimeout(txSuccess: Boolean, msg: MsgTimeout, logs: Abci.ABCIMessageLog): LedgerInfo {
        val ledger = LedgerInfo()
        ledger.logs = logs
        ledger.ackType = IbcAckType.TIMEOUT
        if (txSuccess) {
            ledger.ack = true
            val typed = logs.eventsList.associateBy { it.type }
            ledger.channel = typed["timeout_packet"]!!.let { event ->
                val port = event.attributesList.first { it.key == "packet_src_port" }.value
                val channel = event.attributesList.first { it.key == "packet_src_channel" }.value
                IbcChannelRecord.findBySrcPortSrcChannel(port, channel)
            }
            typed.forEach { (k, v) ->
                when (k) {
                    "timeout_packet" -> v.attributesList.forEach {
                        when (it.key) {
                            "packet_sequence" -> ledger.sequence = it.value.toInt()
                        }
                    }
                    "timeout" -> ledger.changesEffected = true
                }
            }
        } else {
            ledger.channel = IbcChannelRecord.findBySrcPortSrcChannel(
                msg.packet.sourcePort,
                msg.packet.sourceChannel
            )
            ledger.sequence = msg.packet.sequence.toInt()
        }
        return ledger
    }

    fun parseTimeoutOnClose(txSuccess: Boolean, msg: MsgTimeoutOnClose, logs: Abci.ABCIMessageLog): LedgerInfo {
        val ledger = LedgerInfo()
        ledger.logs = logs
        ledger.ackType = IbcAckType.TIMEOUT
        if (txSuccess) {
            ledger.ack = true
            val typed = logs.eventsList.associateBy { it.type }
            ledger.channel = typed["timeout_packet"]!!.let { event ->
                val port = event.attributesList.first { it.key == "packet_src_port" }.value
                val channel = event.attributesList.first { it.key == "packet_src_channel" }.value
                IbcChannelRecord.findBySrcPortSrcChannel(port, channel)
            }
            typed.forEach { (k, v) ->
                when (k) {
                    "timeout_packet" -> v.attributesList.forEach {
                        when (it.key) {
                            "packet_sequence" -> ledger.sequence = it.value.toInt()
                        }
                    }
                    "timeout" -> ledger.changesEffected = true
                }
            }
        } else {
            ledger.channel = IbcChannelRecord.findBySrcPortSrcChannel(
                msg.packet.sourcePort,
                msg.packet.sourceChannel
            )
            ledger.sequence = msg.packet.sequence.toInt()
        }
        return ledger
    }

    fun getIbcDenomList(page: Int, count: Int): PagedResults<IbcDenomListed> {
        val list =
            MarkerCacheRecord
                .findIbcPaginated(page.toOffset(count), count)
                .map {
                    IbcDenomListed(
                        it.denom,
                        it.supply.toBigInteger().toString(),
                        it.lastTx?.toString()
                    )
                }
        val total = MarkerCacheRecord.findCountByIbc()
        return PagedResults(total.pageCountOfResults(count), list, total)
    }

    fun getIbcDenomDetail(ibcHash: String) =
        assetService.getAssetFromDB(ibcHash.getIbcDenom())
            ?.let { (id, record) ->
                val txCount = TxMarkerJoinRecord.findCountByDenom(id.value)
                IbcDenomDetail(
                    record.denom,
                    record.supply.toBigInteger().toString(),
                    0,
                    txCount,
                    assetService.getDenomMetadataSingle(record.denom).toObjectNode(protoPrinter),
                    getDenomTrace(ibcHash).toObjectNode(protoPrinter)
                )
            } ?: throw ResourceNotFoundException("Invalid asset: ${ibcHash.getIbcDenom()}")

    fun getDenomTrace(ibcHash: String) = runBlocking { ibcClient.getDenomTrace(ibcHash) }

    fun getBalanceListByDenom() = transaction {
        IbcLedgerRecord.getByDenom().map {
            Balance(
                it.denom,
                it.denomTrace,
                it.balanceIn?.toCoinStr(it.denom),
                it.balanceOut?.toCoinStr(it.denom),
                it.lastTx.toString()
            )
        }
    }

    fun getBalanceListByChain() = transaction {
        IbcLedgerRecord.getByChain().groupBy { it.dstChainName }
            .map { (k, v) ->
                val balances = v.map { ledger ->
                    Balance(
                        ledger.denom,
                        ledger.denomTrace,
                        ledger.balanceIn?.toCoinStr(ledger.denom),
                        ledger.balanceOut?.toCoinStr(ledger.denom),
                        ledger.lastTx.toString()
                    )
                }
                BalancesByChain(k!!, v.maxOf { it.lastTx }.toString(), balances)
            }
    }

    fun getBalanceListByChannel(srcPort: String?, srcChannel: String?) = transaction {
        IbcLedgerRecord.getByChannel(srcPort, srcChannel).groupBy { it.dstChainName }
            .map { (k, v) ->
                val channels = v.groupBy { it.srcChannel }.map { (_, list) ->
                    val (src, dst) = list.first().let {
                        Channel(it.srcPort!!, it.srcChannel!!) to Channel(it.dstPort!!, it.dstChannel!!)
                    }
                    val balances = list.map { ledger ->
                        Balance(
                            ledger.denom,
                            ledger.denomTrace,
                            ledger.balanceIn?.toCoinStr(ledger.denom),
                            ledger.balanceOut?.toCoinStr(ledger.denom),
                            ledger.lastTx.toString()
                        )
                    }
                    BalanceByChannel(src, dst, list.maxOf { it.lastTx }.toString(), balances)
                }
                BalancesByChannel(k!!, v.maxOf { it.lastTx }.toString(), channels)
            }
    }

    fun getChannelsByStatus(status: ChannelOuterClass.State) = transaction {
        IbcChannelRecord.findByStatus(status.name).groupBy { it.dstChainName }
            .map { (chain, list) ->
                val channels = list.map { channel ->
                    val src = Channel(channel.srcPort, channel.srcChannel)
                    val dst = Channel(channel.dstPort, channel.dstChannel)
                    ChannelStatus(src, dst, channel.status)
                }.sortedBy { it.srcChannel.channel }
                IbcChannelStatus(chain, channels)
            }.sortedBy { it.dstChainId }
    }

    fun getChannelIdsByChain(chain: String) = IbcChannelRecord.findByChain(chain).map { it.id.value }

    fun getChannelIdByPortAndChannel(port: String, channel: String) =
        IbcChannelRecord.findBySrcPortSrcChannel(port, channel)?.id?.value ?: -1

    fun getRelayersForChannel(srcPort: String, srcChannel: String) = transaction {
        val channelId = IbcChannelRecord.findBySrcPortSrcChannel(srcPort, srcChannel)?.id?.value
            ?: throw ResourceNotFoundException("Invalid port and channel: $srcPort / $srcChannel")
        IbcRelayerRecord.getRelayersForChannel(channelId)
    }
}

fun String.getIbcHash() = this.split("ibc/").last()
fun String.getIbcDenom() = "ibc/$this"
fun Transfer.DenomTrace.toFullPath() = if (this.path == "") this.baseDenom else "${this.path}/${this.baseDenom}"
