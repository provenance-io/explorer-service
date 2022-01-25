package io.provenance.explorer.service

import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.util.JsonFormat
import ibc.applications.transfer.v1.Transfer
import ibc.core.channel.v1.ChannelOuterClass
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.IbcChannelRecord
import io.provenance.explorer.domain.entities.IbcLedgerRecord
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.TxMarkerJoinRecord
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
import io.provenance.explorer.grpc.v1.IbcGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class IbcService(
    private val ibcClient: IbcGrpcClient,
    private val assetService: AssetService,
    private val accountService: AccountService,
    private val protoPrinter: JsonFormat.Printer,
    private val props: ExplorerProperties
) {
    protected val logger = logger(IbcService::class)

    fun saveIbcChannel(port: String, channel: String) = transaction {
        val channelRes = ibcClient.getChannel(port, channel)
        val client = ibcClient.getClientForChannel(port, channel)
        val escrowAddr = ibcClient.getEscrowAddress(port, channel, props.provAccPrefix())
        val escrowAccount = accountService.getAccountRaw(escrowAddr)
        IbcChannelRecord.getOrInsert(port, channel, channelRes, client, escrowAccount)
    }

    fun buildIbcLedger(ledger: LedgerInfo, txData: TxData) = transaction { IbcLedgerRecord.buildInsert(ledger, txData) }

    fun parseTransfer(ledger: LedgerInfo): LedgerInfo {
        ledger.denomTrace =
            if (ledger.denom.startsWith("ibc")) getDenomTrace(ledger.denom.getIbcHash()).toFullPath() else ledger.denom
        ledger.logs.eventsList.associateBy { it.type }.forEach { (k, v) ->
            when (k) {
                "transfer" -> v.attributesList.first { it.key == "recipient" }
                    .let { ledger.passThroughAddress = accountService.getAccountRaw(it.value) }
                "ibc_transfer" -> v.attributesList.forEach {
                    when (it.key) {
                        "receiver" -> ledger.toAddress = it.value
                        "sender" -> ledger.fromAddress = it.value
                    }
                }
            }
        }
        return ledger
    }

    fun parseRecv(ledger: LedgerInfo): LedgerInfo {
        ledger.logs.eventsList.associateBy { it.type }.forEach { (k, v) ->
            when (k) {
                "denomination_trace" -> v.attributesList.first { it.key == "denom" }.let { ledger.denom = it.value }
                "recv_packet" -> v.attributesList.first { it.key == "packet_data" }.value.toObjectNode().let {
                    ledger.fromAddress = it["sender"].asText()
                    ledger.toAddress = it["receiver"].asText()
                    ledger.denomTrace = it["denom"].asText()
                    ledger.balanceIn = it["amount"].asText()
                }
                "transfer" -> v.attributesList.first { it.key == "sender" }
                    .let { ledger.passThroughAddress = accountService.getAccountRaw(it.value) }
            }
        }
        return ledger
    }

    fun parseAcknowledge(ledger: LedgerInfo, data: ObjectNode): LedgerInfo {
        ledger.fromAddress = data["sender"].asText()
        ledger.toAddress = data["receiver"].asText()
        ledger.denomTrace = data["denom"].asText()
        ledger.balanceOut = data["amount"].asText()
        ledger.logs.eventsList.associateBy { it.type }["fungible_token_packet"]!!.let { e ->
            e.attributesList.firstOrNull { it.key == "success" }?.let { ledger.ack = true }
        }
        return ledger
    }

    fun getIbcDenomList(
        page: Int,
        count: Int
    ): PagedResults<IbcDenomListed> {
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

    fun getDenomTrace(ibcHash: String) = ibcClient.getDenomTrace(ibcHash)

    fun getBalanceListByDenom() = transaction {
        IbcLedgerRecord.getByDenom().map {
            Balance(
                it.denom,
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
                        ledger.balanceIn?.toCoinStr(ledger.denom),
                        ledger.balanceOut?.toCoinStr(ledger.denom),
                        ledger.lastTx.toString()
                    )
                }
                BalancesByChain(k!!, v.maxOf { it.lastTx }.toString(), balances)
            }
    }

    fun getBalanceListByChannel() = transaction {
        IbcLedgerRecord.getByChannel().groupBy { it.dstChainName }
            .map { (k, v) ->
                val channels = v.groupBy { it.srcChannel }.map { (_, list) ->
                    val (src, dst) = list.first().let {
                        Channel(it.srcPort!!, it.srcChannel!!) to Channel(it.dstPort!!, it.dstChannel!!)
                    }
                    val balances = list.map { ledger ->
                        Balance(
                            ledger.denom,
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
}

fun String.getIbcHash() = this.split("ibc/").last()
fun String.getIbcDenom() = "ibc/$this"
fun Transfer.DenomTrace.toFullPath() = if (this.path == "") this.baseDenom else "${this.path}/${this.baseDenom}"
