package io.provenance.explorer.service

import com.google.protobuf.Timestamp
import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BaseDenomType
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
import io.provenance.explorer.domain.models.explorer.CoinStr
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
class AssetService (
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
                        CoinStr(it.supply.toBigInteger().toString(), it.denom),
                        it.status.prettyStatus(),
                        it.data?.isMintable() ?: false,
                        it.lastTx?.toString(),
                        it.markerType.prettyMarkerType()) }
        val total = MarkerCacheRecord.findCountByStatus(statuses)
        return PagedResults(total.pageCountOfResults(count), list, total)
    }

    fun getAssetRaw(denom: String) = transaction {
        getAssetFromDB(denom) ?: getAndInsertMarker(denom)
    }

    fun getAssetFromDB(denom: String) = transaction {
        MarkerCacheRecord.findByDenom(denom)?.let { Pair(it.id, it) }
    }

    private fun getAndInsertMarker(denom: String) =
        markerClient.getMarkerDetail(denom)?.let {
            MarkerCacheRecord.insertIgnore(
                it.baseAccount.address,
                it.markerType.name,
                it.denom,
                it.status.toString(),
                it,
                accountService.getCurrentSupply(denom).toBigDecimal(),
                TxMarkerJoinRecord.findLatestTxByDenom(denom)
            )
        } ?: accountService.getCurrentSupply(denom).let {
                MarkerCacheRecord.insertIgnore(
                    null,
                    denom.getBaseDenomType().name,
                    denom,
                    MarkerStatus.MARKER_STATUS_ACTIVE.toString(),
                    null,
                    it.toBigDecimal(),
                    TxMarkerJoinRecord.findLatestTxByDenom(denom)
                )
            }

    fun getAssetDetail(denom: String) =
        getAssetFromDB(denom)
            ?.let { (id, record) ->
                val txCount = TxMarkerJoinRecord.findCountByDenom(id.value)
                AssetDetail(
                    record.denom,
                    record.markerAddress,
                    if (record.data != null) AssetManagement(record.data!!.getManagingAccounts(), record.data!!.allowGovernanceControl) else null,
                    CoinStr(record.supply.toBigInteger().toString(), record.denom),
                    record.data?.isMintable() ?: false,
                    if (record.markerAddress != null) markerClient.getMarkerHolders(denom, 0, 10).pagination.total.toInt() else 0,
                    txCount,
                    attrClient.getAllAttributesForAddress(record.markerAddress).map { attr -> attr.toResponse() },
                    accountService.getDenomMetadataSingle(denom).toObjectNode(protoPrinter),
                    TokenCounts(
                        if (record.markerAddress != null) accountService.getBalances(record.markerAddress!!, 0, 1).pagination.total else 0,
                        if (record.markerAddress != null) metadataClient.getScopesByOwner(record.markerAddress!!).pagination.total.toInt() else 0),
                    record.status.prettyStatus(),
                    record.markerType.prettyMarkerType()
                )
            } ?: throw ResourceNotFoundException("Invalid asset: $denom")

    fun getAssetHolders(denom: String, page: Int, count: Int) = accountService.getCurrentSupply(denom).let { supply ->
        val res = markerClient.getMarkerHolders(denom, page.toOffset(count), count)
        val list = res.balancesList.map { bal ->
                val balance = bal.coinsList.first { coin -> coin.denom == denom }.amount
                AssetHolder(bal.address, CountStrTotal(balance, supply, denom))
            }.sortedByDescending { it.balance.count }
        PagedResults(res.pagination.total.pageCountOfResults(count), list, res.pagination.total)
    }

    fun getMetadata(denom: String?) = accountService.getDenomMetadata(denom).map { it.toObjectNode(protoPrinter) }

    // Updates the Marker cache
    fun updateAssets(denoms: Set<String>, txTime: Timestamp) = transaction {
        logger.info("saving assets")
        denoms.forEach { marker ->
            val data = markerClient.getMarkerDetail(marker)
            MarkerCacheRecord.findByDenom(marker)?.apply {
                if (data != null) this.status = data.status.toString()
                this.supply = accountService.getCurrentSupply(marker).toBigDecimal()
                this.lastTx = txTime.toDateTime()
                this.data = data
            }
        }
    }
}

fun String.getDenomByAddress() = MarkerCacheRecord.findByAddress(this)?.denom

fun String.prettyStatus() = this.substringAfter("MARKER_STATUS_")
fun String.prettyMarkerType() = if (this.startsWith("MARKER_TYPE")) this.substringAfter("MARKER_TYPE_") else this
fun String.prettyRole() = this.substringAfter("ACCESS_")

fun String.getBaseDenomType() =
    when {
        this.startsWith("ibc/") -> BaseDenomType.IBC_DENOM
        else -> BaseDenomType.DENOM
    }
