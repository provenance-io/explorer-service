package io.provenance.explorer.client

import com.fasterxml.jackson.databind.node.ObjectNode
import feign.Headers
import feign.Param
import feign.RequestLine
import io.provenance.explorer.model.AssetDetail
import io.provenance.explorer.model.AssetHolder
import io.provenance.explorer.model.AssetListed
import io.provenance.explorer.model.IbcDenomDetail
import io.provenance.explorer.model.base.PagedResults
import io.provenance.marker.v1.MarkerStatus

object AssetRoutes {
    const val ASSETS_V2 = "${BaseRoutes.V2_BASE}/assets"
    const val ASSETS_V3 = "${BaseRoutes.V3_BASE}/assets"
    const val ALL = "$ASSETS_V2/all"
    const val DETAIL = "$ASSETS_V3/{denom}"
    const val DETAIL_IBC = "$ASSETS_V3/ibc/{hash}"
    const val HOLDERS = "$ASSETS_V3/{denom}/holders"
    const val METADATA = "$ASSETS_V3/metadata"
}

@Headers(BaseClient.CT_JSON)
interface AssetClient : BaseClient {

    @RequestLine("GET ${AssetRoutes.ALL}")
    fun all(
        @Param("statuses") statuses: List<MarkerStatus> = listOf(MarkerStatus.MARKER_STATUS_ACTIVE),
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<AssetListed>

    @RequestLine("GET ${AssetRoutes.DETAIL}")
    fun asset(@Param("denom") denom: String): AssetDetail

    @RequestLine("GET ${AssetRoutes.DETAIL_IBC}")
    fun ibcAsset(@Param("hash") hash: String): IbcDenomDetail

    @RequestLine("GET ${AssetRoutes.HOLDERS}")
    fun assetHolders(
        @Param("denom") denom: String,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<AssetHolder>

    @RequestLine("GET ${AssetRoutes.METADATA}")
    fun metadata(@Param("denom") denom: String? = null): List<ObjectNode>
}
