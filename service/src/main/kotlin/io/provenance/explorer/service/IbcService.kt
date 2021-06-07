package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import ibc.core.channel.v1.ChannelOuterClass
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.IbcChannelRecord
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.TxMarkerJoinRecord
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.Channel
import io.provenance.explorer.domain.models.explorer.ChannelBalance
import io.provenance.explorer.domain.models.explorer.ChannelStatus
import io.provenance.explorer.domain.models.explorer.IbcChannelBalance
import io.provenance.explorer.domain.models.explorer.IbcChannelStatus
import io.provenance.explorer.domain.models.explorer.IbcDenomDetail
import io.provenance.explorer.domain.models.explorer.IbcDenomListed
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.toData
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
                        it.lastTx?.toString()) }
        return PagedResults(MarkerCacheRecord.findCountByIbc().pageCountOfResults(count), list)
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
                    accountService.getDenomMetadataSingle(record.denom).toObjectNode(protoPrinter),
                    ibcClient.getDenomTrace(ibcHash).toObjectNode(protoPrinter)
                )
            } ?: throw ResourceNotFoundException("Invalid asset: ${ibcHash.getIbcDenom()}")

    fun getBalanceList() = transaction {
        IbcChannelRecord.getAll().groupBy { it.dstChainName }
            .map { (chain, list) ->
                val channels = list.mapNotNull mp@{ channel ->
                    val bals = accountService.getBalances(channel.escrowAddr, 1, 100)
                        .balancesList.map { it.toData() }.sortedByDescending { it.amount }
                    if (bals.isEmpty()) return@mp null
                    val src = Channel(channel.srcPort, channel.srcChannel)
                    val dst = Channel(channel.dstPort, channel.dstChannel)
                    ChannelBalance(src, dst, bals)
                }.sortedBy { it.srcChannel.channel }
                IbcChannelBalance(chain, channels)
            }.sortedBy { it.dstChainId }
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

fun String.getIbcDenom() = "ibc/$this"
