package io.provenance.explorer.service

import com.google.protobuf.Timestamp
import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.TxMarkerJoinRecord
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.AssetDetail
import io.provenance.explorer.domain.models.explorer.AssetHolder
import io.provenance.explorer.domain.models.explorer.AssetListed
import io.provenance.explorer.domain.models.explorer.AssetManagement
import io.provenance.explorer.domain.models.explorer.CountStrTotal
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.TokenCounts
import io.provenance.explorer.grpc.extensions.getManagingAccounts
import io.provenance.explorer.grpc.extensions.isMintable
import io.provenance.explorer.grpc.v1.AttributeGrpcClient
import io.provenance.explorer.grpc.v1.MarkerGrpcClient
import io.provenance.explorer.grpc.v1.MetadataGrpcClient
import io.provenance.marker.v1.MarkerStatus
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class AssetService(
    private val markerClient: MarkerGrpcClient,
    private val attrClient: AttributeGrpcClient,
    private val metadataClient: MetadataGrpcClient,
    private val accountService: AccountService,
    private val protoPrinter: JsonFormat.Printer
) {
    protected val logger = logger(AssetService::class)

    fun getAssets(
        statuses: List<MarkerStatus>,
        page: Int,
        count: Int
    ): PagedResults<AssetListed> {
        val list =
            MarkerCacheRecord
                .findByStatusPaginated(statuses, page.toOffset(count), count)
                .map {
                    AssetListed(
                        it.denom,
                        it.markerAddress,
                        it.supply.toBigInteger().toString(),
                        it.status.prettyStatus(),
                        it.data.isMintable(),
                        if (it.lastTx != null) it.lastTx.toString() else null) }
        return PagedResults(MarkerCacheRecord.findCountByStatus(statuses).pageCountOfResults(count), list)
    }

    fun getAssetRaw(denom: String) = transaction {
        MarkerCacheRecord.findByDenom(denom)?.let { Pair(it.id, it) } ?:
            markerClient.getMarkerDetail(denom).let {
                MarkerCacheRecord.insertIgnore(
                    it, accountService.getCurrentSupply(denom).toBigDecimal(),
                    TxMarkerJoinRecord.findLatestTxByDenom(denom)
                )
            }
    }

    fun getAssetDetail(denom: String) =
        getAssetRaw(denom)
            .let { (id, record) ->
                val txCount = TxMarkerJoinRecord.findCountByDenom(id!!.value)
                AssetDetail(
                    record.denom,
                    record.markerAddress,
                    AssetManagement(record.data.getManagingAccounts(), record.data.allowGovernanceControl),
                    record.supply.toBigInteger().toString(),
                    record.data.isMintable(),
                    markerClient.getMarkerHolders(denom, 0, 10).pagination.total.toInt(),
                    txCount,
                    attrClient.getAllAttributesForAddress(record.markerAddress)
                        .map { attr -> attr.toObjectNode(protoPrinter) },
                    markerClient.getMarkerMetadata(denom).toObjectNode(protoPrinter),
                    TokenCounts(
                        accountService.getBalances(record.markerAddress, 0, 1).pagination.total,
                        metadataClient.getScopesByOwner(record.markerAddress).pagination.total.toInt()),
                    record.status
                )
            }

    fun getAssetHolders(denom: String, page: Int, count: Int) = accountService.getCurrentSupply(denom).let { supply ->
        val res = markerClient.getMarkerHolders(denom, page.toOffset(count), count)
        val list = res.balancesList.map { bal ->
                val balance = bal.coinsList.first { coin -> coin.denom == denom }.amount
                AssetHolder(bal.address, CountStrTotal(balance, supply))
            }.sortedByDescending { it.balance.count }
        PagedResults(res.pagination.total.pageCountOfResults(count), list)
    }

    fun getMetaData(denom: String) = protoPrinter.print(markerClient.getMarkerMetadata(denom))

    // Updates the Marker cache
    fun updateAssets(denoms: Set<String>, txTime: Timestamp) = transaction {
        logger.info("saving assets")
        denoms.forEach { marker ->
            val data = markerClient.getMarkerDetail(marker)
            val record = MarkerCacheRecord.findByDenom(marker)!!
            record.apply {
                this.status = data.status.toString()
                this.supply = accountService.getCurrentSupply(marker).toBigDecimal()
                this.lastTx = txTime.toDateTime()
                this.data = data
            }
        }
    }
}

fun String.getDenomByAddress() = MarkerCacheRecord.findByAddress(this)?.denom

fun String.prettyStatus() = this.substringAfter("MARKER_STATUS_")

fun String.prettyRole() = this.substringAfter("ACCESS_")
