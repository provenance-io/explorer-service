package io.provenance.explorer.client

import feign.Headers
import feign.Param
import feign.RequestLine
import io.provenance.explorer.model.BlockSummary
import io.provenance.explorer.model.base.PagedResults

object BlockRoutes {
    const val BLOCKS_V2 = "${BaseRoutes.V2_BASE}/blocks"
    const val CURRENT_HEIGHT = "$BLOCKS_V2/height"
    const val AT_HEIGHT = "$BLOCKS_V2/height/{height}"
    const val RECENT = "$BLOCKS_V2/recent"
}

@Headers(BaseClient.CT_JSON)
interface BlockClient : BaseClient {

    @RequestLine("GET ${BlockRoutes.RECENT}")
    fun recent(@Param("count") count: Int = 10, @Param("page") page: Int = 1): PagedResults<BlockSummary>

    @RequestLine("GET ${BlockRoutes.CURRENT_HEIGHT}")
    fun currentHeight(): BlockSummary

    @RequestLine("GET ${BlockRoutes.AT_HEIGHT}")
    fun atHeight(@Param("height") height: Int): BlockSummary
}
