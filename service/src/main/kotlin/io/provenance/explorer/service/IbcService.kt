package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import cosmos.base.abci.v1beta1.Abci
import cosmos.tx.v1beta1.ServiceOuterClass
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
import io.provenance.explorer.domain.entities.BlockTxRetryRecord
import io.provenance.explorer.domain.entities.IbcAckType
import io.provenance.explorer.domain.entities.IbcChannelRecord
import io.provenance.explorer.domain.entities.IbcLedgerAckRecord
import io.provenance.explorer.domain.entities.IbcLedgerRecord
import io.provenance.explorer.domain.entities.IbcRelayerRecord
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.TxIbcRecord
import io.provenance.explorer.domain.entities.TxMarkerJoinRecord
import io.provenance.explorer.domain.exceptions.InvalidArgumentException
import io.provenance.explorer.domain.extensions.decodeHex
import io.provenance.explorer.domain.extensions.getFirstSigner
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.LedgerInfo
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.TxUpdate
import io.provenance.explorer.grpc.extensions.denomEventRegexParse
import io.provenance.explorer.grpc.extensions.eventsAtIndex
import io.provenance.explorer.grpc.extensions.getIbcLedgerMsgs
import io.provenance.explorer.grpc.extensions.getTxIbcClientChannel
import io.provenance.explorer.grpc.extensions.isIbcTransferMsg
import io.provenance.explorer.grpc.extensions.mapEventAttrValues
import io.provenance.explorer.grpc.extensions.scrubQuotes
import io.provenance.explorer.grpc.extensions.toMsgAcknowledgement
import io.provenance.explorer.grpc.extensions.toMsgIbcTransferRequest
import io.provenance.explorer.grpc.extensions.toMsgRecvPacket
import io.provenance.explorer.grpc.extensions.toMsgTimeout
import io.provenance.explorer.grpc.extensions.toMsgTimeoutOnClose
import io.provenance.explorer.grpc.extensions.toMsgTransfer
import io.provenance.explorer.grpc.v1.IbcGrpcClient
import io.provenance.explorer.model.Balance
import io.provenance.explorer.model.BalanceByChannel
import io.provenance.explorer.model.BalancesByChain
import io.provenance.explorer.model.BalancesByChannel
import io.provenance.explorer.model.Channel
import io.provenance.explorer.model.ChannelStatus
import io.provenance.explorer.model.IbcChannelStatus
import io.provenance.explorer.model.IbcDenomDetail
import io.provenance.explorer.model.IbcDenomListed
import io.provenance.explorer.model.base.PagedResults
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class IbcService(
    private val ibcClient: IbcGrpcClient,
    private val assetService: AssetService,
    private val accountService: AccountService,
    private val protoPrinter: JsonFormat.Printer
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

    /**
     * Parses the transfer-related events from an IBC `MsgTransfer` message and constructs a `LedgerInfo` object
     * containing detailed information about the transfer.
     *
     * @param msg The IBC `MsgTransfer` message representing the transfer operation being processed.
     * @param events A list of `Abci.StringEvent` objects that contain various events related to the IBC message.
     *
     * @return A `LedgerInfo` object populated with the parsed transfer details.
     *
     * @throws Exception if required event attributes (such as `packet_src_port`, `packet_src_channel`, or `send_packet`)
     *                   are missing or if the associated IBC channel cannot be found.
     *
     * The method performs the following steps:
     * 1. Maps the events by their type using the `associateBy` function for easy lookup.
     * 2. Sets the `ackType` of the `LedgerInfo` to `IbcAckType.TRANSFER`.
     * 3. Looks for the "send_packet" event to extract the source port and channel, and uses these to find the
     *    corresponding IBC channel. If the channel is not found, it logs an error and throws an exception.
     * 4. Iterates over all events to extract additional transfer-related information:
     *    - For the "transfer" event, it retrieves the recipient's address and sets it in `LedgerInfo`.
     *    - For the "send_packet" event, it decodes the packet data to extract and populate fields such as the
     *      denomination (`denom`), the trace of the denomination (`denomTrace`), the sender and receiver addresses,
     *      and the amount being transferred (`balanceOut`).
     *    - Extracts the packet sequence number and stores it in `LedgerInfo`.
     */
    fun parseTransfer(msg: MsgTransfer, events: List<Abci.StringEvent>): LedgerInfo {
        val typed = events.associateBy { it.type }
        val ledger = LedgerInfo()
        ledger.ackType = IbcAckType.TRANSFER
        typed["send_packet"]?.let { event ->
            val port = event.attributesList.firstOrNull { it.key == "packet_src_port" }?.value
            val channel = event.attributesList.firstOrNull { it.key == "packet_src_channel" }?.value

            if (port != null && channel != null) {
                ledger.channel = IbcChannelRecord.findBySrcPortSrcChannel(port, channel)
                if (ledger.channel == null) {
                    val errorMsg = "No IBC channel found for port: $port, channel: $channel"
                    logger.error(errorMsg)
                    throw Exception(errorMsg)
                }
            } else {
                val errorMsg = "Missing required attributes 'packet_src_port' or 'packet_src_channel' in event: $event"
                logger.error(errorMsg)
                throw Exception(errorMsg)
            }
        } ?: run {
            val errorMsg = "IBC send_packet not found in map: $typed"
            logger.error(errorMsg)
            throw Exception(errorMsg)
        }
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
                            if (ledger.denom.isEmpty()) {
                                it.value.decodeHex().toObjectNode().let { node ->
                                    ledger.denom = node.get("denom").asText()
                                    ledger.denomTrace =
                                        if (ledger.denom.startsWith("ibc")) getDenomTrace(ledger.denom.getIbcHash()).toFullPath() else ledger.denom
                                    ledger.toAddress = node.get("receiver").asText()
                                    ledger.fromAddress = node.get("sender").asText()
                                    ledger.balanceOut = node.get("amount").asText()
                                }
                            }

                        "packet_data" ->
                            if (ledger.denom.isEmpty()) {
                                it.value.toObjectNode().let { node ->
                                    ledger.denom = node.get("denom").asText()
                                    ledger.denomTrace =
                                        if (ledger.denom.startsWith("ibc")) getDenomTrace(ledger.denom.getIbcHash()).toFullPath() else ledger.denom
                                    ledger.toAddress = node.get("receiver").asText()
                                    ledger.fromAddress = node.get("sender").asText()
                                    ledger.balanceOut = node.get("amount").asText()
                                }
                            }

                        "packet_sequence" -> ledger.sequence = it.value.toInt()
                    }
                }
            }
        }
        return ledger
    }

    /**
     * Parses the receive packet-related events from an IBC `MsgRecvPacket` message and constructs a `LedgerInfo` object
     * containing detailed information about the receipt of the packet.
     *
     * @param txSuccess A boolean indicating whether the transaction was successful.
     * @param msg The IBC `MsgRecvPacket` message representing the receive packet operation being processed.
     * @param events A list of `Abci.StringEvent` objects that contain various events related to the IBC message.
     *
     * @return A `LedgerInfo` object populated with the parsed receive packet details.
     *
     * @throws Exception if required event attributes (such as `packet_dst_port`, `packet_dst_channel`, or `recv_packet`)
     *                   are missing, or if the associated IBC channel cannot be found.
     *
     * The method performs the following steps:
     * 1. Initializes a `LedgerInfo` object, setting the acknowledgment type to `IbcAckType.RECEIVE` and marking the ledger
     *    as an inbound movement (`movementIn = true`).
     * 2. If `txSuccess` is true:
     *    - Sets the acknowledgment (`ack`) to true.
     *    - Maps the events by their type using the `associateBy` function for easy lookup.
     *    - Looks for the "recv_packet" event to extract the destination port and channel, and uses these to find the
     *      corresponding IBC channel. If the channel is not found, it logs an error and throws an exception.
     *    - Iterates over all events to extract additional packet-related information:
     *      - For the "recv_packet" event, it decodes the packet data to extract and populate fields such as the
     *        sender and receiver addresses, the amount being transferred (`balanceIn`), and the packet sequence.
     *      - For the "transfer" event, it retrieves the sender's address, parses the denomination (`denom`), and traces
     *        the denomination (`denomTrace`).
     *      - Marks that changes were effected and the acknowledgment was successful (`ackSuccess = true`).
     * 3. If `txSuccess` is false:
     *    - Retrieves the IBC channel based on the destination port and channel from the `msg`.
     *    - Extracts the packet sequence from the `msg`.
     *
     * @return A populated `LedgerInfo` object with the details of the receive packet.
     */
    fun parseRecv(txSuccess: Boolean, msg: MsgRecvPacket, events: List<Abci.StringEvent>): LedgerInfo {
        val ledger = LedgerInfo()
        ledger.ackType = IbcAckType.RECEIVE
        ledger.movementIn = true
        if (txSuccess) {
            ledger.ack = true
            val typed = events.associateBy { it.type }
            typed["recv_packet"]?.let { event ->
                val port = event.attributesList.firstOrNull { it.key == "packet_dst_port" }?.value
                val channel = event.attributesList.firstOrNull { it.key == "packet_dst_channel" }?.value

                if (port != null && channel != null) {
                    ledger.channel = IbcChannelRecord.findBySrcPortSrcChannel(port, channel)
                } else {
                    val errorMsg =
                        "Missing required attributes 'packet_dst_port' or 'packet_dst_channel' in event: $event"
                    logger.error(errorMsg)
                    throw Exception(errorMsg)
                }
            } ?: run {
                val errorMsg = "IBC recv_packet not found in map: $typed"
                logger.error(errorMsg)
                throw Exception(errorMsg)
            }
            typed.forEach { (k, v) ->
                when (k) {
                    "recv_packet" -> v.attributesList.forEach {
                        when (it.key) {
                            "packet_data_hex" ->
                                if (ledger.toAddress.isEmpty()) {
                                    it.value.decodeHex().toObjectNode().let { node ->
                                        ledger.toAddress = node.get("receiver").asText()
                                        ledger.fromAddress = node.get("sender").asText()
                                        ledger.balanceIn = node.get("amount").asText()
                                    }
                                }

                            "packet_data" ->
                                if (ledger.toAddress.isEmpty()) {
                                    it.value.toObjectNode().let { node ->
                                        ledger.toAddress = node.get("receiver").asText()
                                        ledger.fromAddress = node.get("sender").asText()
                                        ledger.balanceIn = node.get("amount").asText()
                                    }
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

    /**
     * Parses the acknowledgment-related events from an IBC `MsgAcknowledgement` message and constructs a `LedgerInfo` object
     * containing detailed information about the acknowledgment process.
     *
     * @param txSuccess A boolean indicating whether the transaction was successful.
     * @param msg The IBC `MsgAcknowledgement` message representing the acknowledgment operation being processed.
     * @param events A list of `Abci.StringEvent` objects that contain various events related to the IBC message.
     *
     * @return A `LedgerInfo` object populated with the parsed acknowledgment details.
     *
     * @throws Exception if required event attributes (such as `packet_src_port`, `packet_src_channel`, or `acknowledge_packet`)
     *                   are missing, or if the associated IBC channel cannot be found.
     *
     * The method performs the following steps:
     * 1. Initializes a `LedgerInfo` object, setting the acknowledgment type to `IbcAckType.ACKNOWLEDGEMENT`.
     * 2. If `txSuccess` is true:
     *    - Sets the acknowledgment (`ack`) to true.
     *    - Maps the events by their type using the `associateBy` function for easy lookup.
     *    - Looks for the "acknowledge_packet" event to extract the source port and channel, and uses these to find the
     *      corresponding IBC channel. If the channel is not found, it logs an error and throws an exception.
     *    - Iterates over all events to extract additional acknowledgment-related information:
     *      - For the "acknowledge_packet" event, it extracts and sets the packet sequence in `LedgerInfo`.
     *      - For the "fungible_token_packet" event, it checks for a "success" attribute to mark that changes were
     *        effected and the acknowledgment was successful (`ackSuccess = true`).
     * 3. If `txSuccess` is false:
     *    - Retrieves the IBC channel based on the source port and channel from the `msg`.
     *    - Extracts the packet sequence from the `msg`.
     *
     * @return A populated `LedgerInfo` object with the details of the acknowledgment.
     */
    fun parseAcknowledge(txSuccess: Boolean, msg: MsgAcknowledgement, events: List<Abci.StringEvent>): LedgerInfo {
        val ledger = LedgerInfo()
        ledger.ackType = IbcAckType.ACKNOWLEDGEMENT
        if (txSuccess) {
            ledger.ack = true
            val typed = events.associateBy { it.type }
            typed["acknowledge_packet"]?.let { event ->
                val port = event.attributesList.firstOrNull { it.key == "packet_src_port" }?.value
                val channel = event.attributesList.firstOrNull { it.key == "packet_src_channel" }?.value
                if (port != null && channel != null) {
                    ledger.channel = IbcChannelRecord.findBySrcPortSrcChannel(port, channel)
                } else {
                    val errorMsg =
                        "Missing required attributes 'packet_src_port' or 'packet_src_channel' in event: $event"
                    logger.error(errorMsg)
                    throw Exception(errorMsg)
                }
            } ?: run {
                val errorMsg = "IBC acknowledge_packet not found in map: $typed"
                logger.error(errorMsg)
                throw Exception(errorMsg)
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

    /**
     * Parses the timeout-related events from an IBC `MsgTimeout` message and constructs a `LedgerInfo` object
     * containing detailed information about the timeout process.
     *
     * @param txSuccess A boolean indicating whether the transaction was successful.
     * @param msg The IBC `MsgTimeout` message representing the timeout operation being processed.
     * @param events A list of `Abci.StringEvent` objects that contain various events related to the IBC message.
     *
     * @return A `LedgerInfo` object populated with the parsed timeout details.
     *
     * @throws Exception if required event attributes (such as `packet_src_port`, `packet_src_channel`, or `timeout_packet`)
     *                   are missing, or if the associated IBC channel cannot be found.
     *
     * The method performs the following steps:
     * 1. Initializes a `LedgerInfo` object, setting the acknowledgment type to `IbcAckType.TIMEOUT`.
     * 2. If `txSuccess` is true:
     *    - Sets the acknowledgment (`ack`) to true.
     *    - Maps the events by their type using the `associateBy` function for easy lookup.
     *    - Looks for the "timeout_packet" event to extract the source port and channel, and uses these to find the
     *      corresponding IBC channel. If the channel is not found, it logs an error and throws an exception.
     *    - Iterates over all events to extract additional timeout-related information:
     *      - For the "timeout_packet" event, it extracts and sets the packet sequence in `LedgerInfo`.
     *      - For the "timeout" event, it marks that changes were effected.
     * 3. If `txSuccess` is false:
     *    - Retrieves the IBC channel based on the source port and channel from the `msg`.
     *    - Extracts the packet sequence from the `msg`.
     *
     * @return A populated `LedgerInfo` object with the details of the timeout.
     */
    fun parseTimeout(txSuccess: Boolean, msg: MsgTimeout, events: List<Abci.StringEvent>): LedgerInfo {
        val ledger = LedgerInfo()
        ledger.ackType = IbcAckType.TIMEOUT
        if (txSuccess) {
            ledger.ack = true
            val typed = events.associateBy { it.type }
            typed["timeout_packet"]?.let { event ->
                val port = event.attributesList.firstOrNull { it.key == "packet_src_port" }?.value
                val channel = event.attributesList.firstOrNull { it.key == "packet_src_channel" }?.value

                if (port != null && channel != null) {
                    ledger.channel = IbcChannelRecord.findBySrcPortSrcChannel(port, channel)
                } else {
                    val errorMsg =
                        "Missing required attributes 'packet_src_port' or 'packet_src_channel' in event: $event"
                    logger.error(errorMsg)
                    throw Exception(errorMsg)
                }
            } ?: run {
                val errorMsg = "IBC timeout_packet not found in map: $typed"
                logger.error(errorMsg)
                throw Exception(errorMsg)
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

    /**
     * Parses the timeout-on-close-related events from an IBC `MsgTimeoutOnClose` message and constructs a `LedgerInfo` object
     * containing detailed information about the timeout-on-close process.
     *
     * @param txSuccess A boolean indicating whether the transaction was successful.
     * @param msg The IBC `MsgTimeoutOnClose` message representing the timeout-on-close operation being processed.
     * @param events A list of `Abci.StringEvent` objects that contain various events related to the IBC message.
     *
     * @return A `LedgerInfo` object populated with the parsed timeout-on-close details.
     *
     * @throws Exception if required event attributes (such as `packet_src_port`, `packet_src_channel`, or `timeout_packet`)
     *                   are missing, or if the associated IBC channel cannot be found.
     *
     * The method performs the following steps:
     * 1. Initializes a `LedgerInfo` object, setting the acknowledgment type to `IbcAckType.TIMEOUT`.
     * 2. If `txSuccess` is true:
     *    - Sets the acknowledgment (`ack`) to true.
     *    - Maps the events by their type using the `associateBy` function for easy lookup.
     *    - Ensures the "timeout_packet" event is present in the mapped events. If not, logs an error.
     *    - Extracts the source port and channel from the "timeout_packet" event and uses these to find the
     *      corresponding IBC channel.
     *    - Iterates over all events to extract additional timeout-on-close-related information:
     *      - For the "timeout_packet" event, it extracts and sets the packet sequence in `LedgerInfo`.
     *      - For the "timeout" event, it marks that changes were effected.
     * 3. If `txSuccess` is false:
     *    - Retrieves the IBC channel based on the source port and channel from the `msg`.
     *    - Extracts the packet sequence from the `msg`.
     *
     * @return A populated `LedgerInfo` object with the details of the timeout-on-close.
     */
    fun parseTimeoutOnClose(txSuccess: Boolean, msg: MsgTimeoutOnClose, events: List<Abci.StringEvent>): LedgerInfo {
        val ledger = LedgerInfo()
        ledger.ackType = IbcAckType.TIMEOUT
        if (txSuccess) {
            ledger.ack = true
            val typed = events.associateBy { it.type }
            if (typed["timeout_packet"] == null) {
                logger.error("IBC timeout_packet not found in map: $typed")
            }
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

    fun saveIbcChannelData(tx: ServiceOuterClass.GetTxResponse, txInfo: TxData, txUpdate: TxUpdate) =
        transaction {
            val scrapedObjs = tx.tx.body.messagesList.map { it.getTxIbcClientChannel() }

            // save channel data
            // save tx_ibc
            // save ibc_relayer
            scrapedObjs.forEachIndexed { idx, obj ->
                if (obj == null) return@forEachIndexed
                // get channel, or null
                val channel = when {
                    obj.msgSrcChannel != null -> saveIbcChannel(obj.msgSrcPort!!, obj.msgSrcChannel)
                    obj.srcChannelAttr != null -> {
                        val (port, channel) = tx.mapEventAttrValues(
                            idx,
                            obj.event,
                            listOf(obj.srcPortAttr!!, obj.srcChannelAttr!!)
                        ).let { it[obj.srcPortAttr]!! to it[obj.srcChannelAttr]!! }
                        saveIbcChannel(port, channel)
                    }

                    else -> null
                }

                val client = when {
                    channel != null -> channel.client
                    obj.msgClient != null -> obj.msgClient
                    obj.clientAttr != null ->
                        tx.mapEventAttrValues(idx, obj.event, listOf(obj.clientAttr))[obj.clientAttr]!!

                    else -> null
                }
                txUpdate.apply { this.ibcJoin.add(TxIbcRecord.buildInsert(txInfo, client, channel?.id?.value)) }

                val msg = tx.tx.body.messagesList[idx]
                if (!msg.isIbcTransferMsg() && client != null) {
                    val relayer = tx.getFirstSigner()
                    val account = accountService.saveAccount(relayer)
                    IbcRelayerRecord.insertIgnore(client, channel?.id?.value, account)
                }
            }

            val txSuccess = tx.txResponse.code == 0
            val successfulRecvHashes = mutableListOf<String>()
            // save ledgers, acks
            tx.tx.body.messagesList.map { it.getIbcLedgerMsgs() }
                .forEachIndexed { idx, any ->
                    if (any == null) return@forEachIndexed
                    val ledger = when {
                        any.typeUrl.endsWith("MsgTransfer") -> {
                            if (!txSuccess) return@forEachIndexed
                            val msg = any.toMsgTransfer()
                            parseTransfer(msg, tx.eventsAtIndex(idx))
                        }

                        any.typeUrl.endsWith("MsgIbcTransferRequest") -> {
                            if (!txSuccess) return@forEachIndexed
                            val msg = any.toMsgIbcTransferRequest()
                            parseTransfer(msg.transfer, tx.eventsAtIndex(idx))
                        }

                        any.typeUrl.endsWith("MsgRecvPacket") -> {
                            val msg = any.toMsgRecvPacket()
                            parseRecv(txSuccess, msg, tx.eventsAtIndex(idx))
                        }

                        any.typeUrl.endsWith("MsgAcknowledgement") -> {
                            val msg = any.toMsgAcknowledgement()
                            parseAcknowledge(txSuccess, msg, tx.eventsAtIndex(idx))
                        }

                        any.typeUrl.endsWith("MsgTimeout") -> {
                            val msg = any.toMsgTimeout()
                            parseTimeout(txSuccess, msg, tx.eventsAtIndex(idx))
                        }

                        any.typeUrl.endsWith("MsgTimeoutOnClose") -> {
                            val msg = any.toMsgTimeoutOnClose()
                            parseTimeoutOnClose(txSuccess, msg, tx.eventsAtIndex(idx))
                        }

                        else -> logger.debug("This typeUrl is not yet supported in as an ibc ledger msg: ${any.typeUrl}")
                            .let { return@forEachIndexed }
                    }
                    when (ledger.ackType) {
                        IbcAckType.ACKNOWLEDGEMENT, IbcAckType.TIMEOUT ->
                            IbcLedgerRecord.findMatchingRecord(ledger, txInfo.txHash)?.let {
                                txUpdate.apply {
                                    this.ibcLedgers.add(buildIbcLedger(ledger, txInfo, it))
                                    this.ibcLedgerAcks.add(buildIbcLedgerAck(ledger, txInfo, it.id.value))
                                }
                            } ?: throw InvalidArgumentException(
                                "No matching IBC ledger record for channel " +
                                        "${ledger.channel!!.srcPort}/${ledger.channel!!.srcChannel}, sequence ${ledger.sequence}"
                            )

                        IbcAckType.RECEIVE ->
                            IbcLedgerRecord.findMatchingRecord(ledger, txInfo.txHash)?.let {
                                txUpdate.apply {
                                    this.ibcLedgerAcks.add(buildIbcLedgerAck(ledger, txInfo, it.id.value))
                                }
                            } ?: if (ledger.changesEffected) {
                                txUpdate.apply {
                                    this.ibcLedgers.add(buildIbcLedger(ledger, txInfo, null))
                                }
                                successfulRecvHashes.add(IbcLedgerRecord.getUniqueHash(ledger))
                            } else if (successfulRecvHashes.contains(IbcLedgerRecord.getUniqueHash(ledger))) {
                                BlockTxRetryRecord.insertNonBlockingRetry(
                                    txInfo.blockHeight,
                                    InvalidArgumentException(
                                        "Matching IBC Ledger record has not been saved yet - " +
                                                "${ledger.channel!!.srcPort}/${ledger.channel!!.srcChannel}, " +
                                                "sequence ${ledger.sequence}. Retrying block to save non-effected RECV record."
                                    )
                                )
                            } else {
                                BlockTxRetryRecord.insertNonBlockingRetry(
                                    txInfo.blockHeight,
                                    InvalidArgumentException(
                                        "Matching IBC Ledger record has not been saved yet - " +
                                                "${ledger.channel!!.srcPort}/${ledger.channel!!.srcChannel}, " +
                                                "sequence ${ledger.sequence}. Could be contained in another Tx in the " +
                                                "same block."
                                    )
                                )
                            }

                        IbcAckType.TRANSFER ->
                            txUpdate.apply { this.ibcLedgers.add(buildIbcLedger(ledger, txInfo, null)) }

                        else -> logger.debug("Invalid IBC ack type: ${ledger.ackType}").let { return@forEachIndexed }
                    }
                }
        }
}

fun String.getIbcHash() = this.split("ibc/").last()
fun String.getIbcDenom() = "ibc/$this"
fun Transfer.DenomTrace.toFullPath() = if (this.path == "") this.baseDenom else "${this.path}/${this.baseDenom}"
