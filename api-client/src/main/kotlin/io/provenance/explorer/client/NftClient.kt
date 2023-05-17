package io.provenance.explorer.client

import com.fasterxml.jackson.databind.JsonNode
import feign.Headers
import feign.Param
import feign.RequestLine
import io.provenance.explorer.client.BaseRoutes.PAGE_PARAMETERS
import io.provenance.explorer.model.ScopeDetail
import io.provenance.explorer.model.ScopeListview
import io.provenance.explorer.model.ScopeRecord
import io.provenance.explorer.model.base.PagedResults

object NftRoutes {
    const val NFT_V2 = "${BaseRoutes.V2_BASE}/nft"
    const val SCOPE = "$NFT_V2/scope/{addr}"
    const val SCOPES_BY_OWNER = "$NFT_V2/scope/owner/{address}"
    const val SCOPE_RECORDS = "$NFT_V2/scope/{addr}/records"
    const val SCOPESPEC_JSON = "$NFT_V2/scopeSpec/{scopeSpec}/json"
    const val CONTRACTSPEC_JSON = "$NFT_V2/contractSpec/{contractSpec}/json"
    const val RECORDSPEC_JSON = "$NFT_V2/recordSpec/{recordSpec}/json"
}

@Headers(BaseClient.CT_JSON)
interface NftClient : BaseClient {

    @RequestLine("GET ${NftRoutes.SCOPE}")
    fun scope(@Param("addr") addr: String): ScopeDetail

    @RequestLine("GET ${NftRoutes.SCOPES_BY_OWNER}?$PAGE_PARAMETERS")
    fun scopesByOwner(
        @Param("address") address: String,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<ScopeListview>

    @RequestLine("GET ${NftRoutes.SCOPE_RECORDS}")
    fun scopeRecords(@Param("addr") addr: String): List<ScopeRecord>

    @RequestLine("GET ${NftRoutes.SCOPESPEC_JSON}")
    fun scopeSpecJson(@Param("scopeSpec") scopeSpec: String): JsonNode

    @RequestLine("GET ${NftRoutes.CONTRACTSPEC_JSON}")
    fun contractSpecJson(@Param("contractSpec") contractSpec: String): JsonNode

    @RequestLine("GET ${NftRoutes.RECORDSPEC_JSON}")
    fun recordSpecJson(@Param("recordSpec") recordSpec: String): JsonNode
}
