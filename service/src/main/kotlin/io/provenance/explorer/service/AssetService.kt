package io.provenance.explorer.service

import io.provenance.explorer.client.PbClient
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.models.clients.pb.MarkerDetail.Companion.getManagingAccounts
import io.provenance.explorer.domain.models.clients.pb.MarkerDetail.Companion.isMintable
import io.provenance.explorer.domain.models.explorer.AssetDetail
import io.provenance.explorer.domain.models.explorer.AssetHolder
import io.provenance.explorer.domain.models.explorer.AssetListed
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.RoundingMode

@Service
class AssetService(private val pbClient: PbClient, private val blockService: BlockService) {

    fun getAllAssets() = pbClient.getMarkers().markers.map {
        MarkerCacheRecord.insertIgnore(it).let { detail ->
            AssetListed(
                detail.denom,
                detail.baseAccount.address,
                blockService.getTotalSupply(detail.denom),
                detail.supply.toBigDecimal()
            )
        }
    }

    private fun getAssetRaw(denom: String) = transaction {
        MarkerCacheRecord.findById(denom)?.data ?:
            pbClient.getMarkerDetail(denom).marker.let { MarkerCacheRecord.insertIgnore(it) }
    }

    fun getAssetDetail(denom: String) =
        (getAssetRaw(denom))
            .let {
                AssetDetail(
                    it.denom,
                    it.baseAccount.address,
                    it.getManagingAccounts(),
                    blockService.getTotalSupply(denom),
                    it.supply.toBigDecimal(),
                    it.isMintable(),
                    getAssetHolders(denom).count(),
                    null // TODO: Figure out how to count txns for this asset
                )
            }

    fun getAssetHolders(denom: String) = blockService.getTotalSupply(denom).let { supply ->
        pbClient.getMarkerHolders(denom).balances
            .map { bal ->
                val balance = bal.coins.first { coin -> coin.denom == denom }.amount.toBigDecimal()
                AssetHolder(bal.address, balance, balance.divide(supply, 8, RoundingMode.CEILING).toDouble())
            }
    }
}
