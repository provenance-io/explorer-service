package io.provenance.explorer.client

import feign.Headers
import feign.Param
import feign.RequestLine
import io.provenance.explorer.model.FeegrantData
import io.provenance.explorer.model.GrantData
import io.provenance.explorer.model.base.PagedResults

object GrantsRoutes {
    const val GRANTS_V3 = "${BaseRoutes.V3_BASE}/grants"
    const val GRANTS_AS_GRANTEE = "$GRANTS_V3/authz/{address}/grantee"
    const val GRANTS_AS_GRANTER = "$GRANTS_V3/authz/{address}/granter"
    const val FEEGRANTS_AS_GRANTEE = "$GRANTS_V3/feegrant/{address}/grantee"
    const val FEEGRANTS_AS_GRANTER = "$GRANTS_V3/feegrant/{address}/granter"
}

@Headers(BaseClient.CT_JSON)
interface GrantsClient : BaseClient {

    @RequestLine("GET ${GrantsRoutes.GRANTS_AS_GRANTEE}")
    fun grantsAsGrantee(
        @Param("address") address: String,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<GrantData>

    @RequestLine("GET ${GrantsRoutes.GRANTS_AS_GRANTER}")
    fun grantsAsGranter(
        @Param("address") address: String,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<GrantData>

    @RequestLine("GET ${GrantsRoutes.FEEGRANTS_AS_GRANTEE}")
    fun feegrantsAsGrantee(
        @Param("address") address: String,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<FeegrantData>

    @RequestLine("GET ${GrantsRoutes.FEEGRANTS_AS_GRANTER}")
    fun feegrantsAsGranter(
        @Param("address") address: String,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<FeegrantData>
}
