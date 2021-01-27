package io.provenance.explorer.service

import io.provenance.explorer.client.PbClient
import org.springframework.stereotype.Service

@Service
class AssetService(private val pbClient: PbClient) {

    fun getAllAssets() = pbClient.getMarkers()

    fun getAssetDetail(id : String) = pbClient.getMarkerDetail(id)

    fun getAssetHolders(id : String) = pbClient.getMarkerHolders(id)
}
