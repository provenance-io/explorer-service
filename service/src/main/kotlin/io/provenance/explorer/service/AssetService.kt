package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.TxMarkerJoinRecord
import io.provenance.explorer.domain.extensions.toHash
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.AssetDetail
import io.provenance.explorer.domain.models.explorer.AssetHolder
import io.provenance.explorer.domain.models.explorer.AssetListed
import io.provenance.explorer.domain.models.explorer.AssetManagement
import io.provenance.explorer.domain.models.explorer.AssetSupply
import io.provenance.explorer.domain.models.explorer.CountStrTotal
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

    fun getAllAssets() = MarkerCacheRecord.findByStatus(listOf(MarkerStatus.MARKER_STATUS_ACTIVE))
        .map {
            AssetListed(
                it.denom,
                it.markerAddress,
                AssetSupply(
                    getTotalSupply(it.denom).toHash(it.denom).first,
                    it.data.supply.toBigInteger().toHash(it.denom).first),
                it.status.prettyStatus()
            )
        }

    fun getAssetRaw(denom: String) = transaction {
        MarkerCacheRecord.findByDenom(denom)?.let { Pair(it.id, it.data) } ?:
            markerClient.getMarkerDetail(denom).let { MarkerCacheRecord.insertIgnore(it) }
    }

    fun getAssetDetail(denom: String) =
        getAssetRaw(denom)
            .let { (id, detail) ->
                val txCount = TxMarkerJoinRecord.findCountByDenom(id!!.value)
                AssetDetail(
                    detail.denom,
                    detail.baseAccount.address,
                    AssetManagement(detail.getManagingAccounts(), detail.allowGovernanceControl),
                    AssetSupply(getTotalSupply(denom).toHash(denom).first, detail.supply.toBigInteger().toHash(denom).first),
                    detail.isMintable(),
                    markerClient.getAllMarkerHolders(denom).size,
                    txCount,
                    attrClient.getAllAttributesForAddress(detail.baseAccount.address)
                        .map { attr -> attr.toObjectNode(protoPrinter) },
                    markerClient.getMarkerMetadata(denom).toObjectNode(protoPrinter),
                    TokenCounts(
                        accountService.getAccountBalances(detail.baseAccount.address).size,
                        metadataClient.getScopesByValueOwner(detail.baseAccount.address).size),
                    detail.status.name.prettyStatus()
                )
            }

    fun getAssetHolders(denom: String, page: Int, count: Int) = getTotalSupply(denom).let { supply ->
        markerClient.getMarkerHolders(denom, page.toOffset(count), count).balancesList
            .map { bal ->
                val balance = bal.coinsList.first { coin -> coin.denom == denom }.amount.toBigInteger()
                AssetHolder(bal.address, CountStrTotal(balance.toHash(denom).first, supply.toHash(denom).first))
            }
    }

    fun getTotalSupply(denom: String) = markerClient.getSupplyByDenom(denom).amount.toBigInteger()

    fun getMetaData(denom: String) = protoPrinter.print(markerClient.getMarkerMetadata(denom))

    // Updates the Marker cache
    fun updateAssets(denoms: Set<String>) = transaction {
        logger.info("saving assets")
        denoms.forEach { marker ->
            val data = markerClient.getMarkerDetail(marker)
            val record = MarkerCacheRecord.findByDenom(marker)!!
            if (data != record.data)
                record.apply {
                    this.status = data.status.toString()
                    this.totalSupply = data.supply.toBigDecimal()
                    this.data = data
                }
        }
    }
}

fun String.getDenomByAddress() =
    MarkerCacheRecord.findByAddress(this)?.denom ?: throw IllegalArgumentException("No denom exists for address $this")

fun String.prettyStatus() = this.substringAfter("MARKER_STATUS_")

fun String.prettyRole() = this.substringAfter("ACCESS_")
