package io.provenance.explorer.client

import feign.Headers
import feign.Param
import feign.RequestLine
import io.provenance.explorer.model.NameObj
import io.provenance.explorer.model.NameTreeResponse
import io.provenance.explorer.model.base.PagedResults

object NameRoutes {
    const val NAMES_V2 = "${BaseRoutes.V2_BASE}/names"
    const val TREE = "$NAMES_V2/tree"
    const val OWNED = "$NAMES_V2/{address}/owned"
}

@Headers(BaseClient.CT_JSON)
interface NameClient : BaseClient {

    @RequestLine("GET ${NameRoutes.TREE}")
    fun tree(): NameTreeResponse

    @RequestLine("GET ${NameRoutes.OWNED}")
    fun ownedByAddress(
        @Param("address") address: String,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<NameObj>
}
