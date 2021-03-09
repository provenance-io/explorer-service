package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.AssetDetail
import io.provenance.explorer.domain.models.explorer.AssetHolder
import io.provenance.explorer.domain.models.explorer.AssetListed
import io.provenance.explorer.domain.models.explorer.AssetSupply
import io.provenance.explorer.domain.models.explorer.TokenCounts
import io.provenance.explorer.grpc.getManagingAccounts
import io.provenance.explorer.grpc.isMintable
import io.provenance.explorer.grpc.v1.AttributeGrpcClient
import io.provenance.explorer.grpc.v1.MarkerGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.RoundingMode

@Service
class AssetService(
    private val markerClient: MarkerGrpcClient,
    private val attrClient: AttributeGrpcClient,
    private val accountService: AccountService,
    private val protoPrinter: JsonFormat.Printer
) {

    fun getAllAssets() = markerClient.getAllMarkers().map {
        MarkerCacheRecord.insertIgnore(it).let { detail ->
            AssetListed(
                detail.denom,
                detail.baseAccount.address,
                AssetSupply(getTotalSupply(detail.denom), detail.supply.toBigInteger())
            )
        }
    }

    private fun getAssetRaw(denom: String) = transaction {
        MarkerCacheRecord.findByDenom(denom)?.data ?:
            markerClient.getMarkerDetail(denom).let { MarkerCacheRecord.insertIgnore(it) }
    }

    fun getAssetDetail(denom: String) =
        getAssetRaw(denom)
            .let {
                val txCount =
                    TxMessageRecord.findByQueryParams(null, denom, listOf(), null, null, 1, 0, null, null).second
                AssetDetail(
                    it.denom,
                    it.baseAccount.address,
                    it.getManagingAccounts(),
                    AssetSupply(getTotalSupply(denom), it.supply.toBigInteger()),
                    it.isMintable(),
                    markerClient.getAllMarkerHolders(denom).size,
                    txCount.toBigInteger(),
                    attrClient.getAllAttributesForAddress(it.baseAccount.address)
                        .map { attr -> attr.toObjectNode(protoPrinter) },
                    markerClient.getMarkerMetadata(denom).toObjectNode(protoPrinter),
                    TokenCounts(
                        accountService.getAccountBalances(it.baseAccount.address).size,
                        0 // TODO: Update with Scope counts
                    )
                )
            }

    fun getAssetHolders(denom: String, page: Int, count: Int) = getTotalSupply(denom).let { supply ->
        markerClient.getMarkerHolders(denom, page.toOffset(count), count).balancesList
            .map { bal ->
                val balance = bal.coinsList.first { coin -> coin.denom == denom }.amount.toBigInteger()
                AssetHolder(
                    bal.address,
                    balance,
                    balance.toBigDecimal().divide(supply.toBigDecimal(), 6, RoundingMode.HALF_UP)
                )
            }
    }

    fun getTotalSupply(denom: String) = markerClient.getSupplyByDenom(denom).amount.toBigInteger()
}

fun String.getDenomByAddress() =
    MarkerCacheRecord.findById(this)?.denom ?: throw IllegalArgumentException("No denom exists for address $this")
