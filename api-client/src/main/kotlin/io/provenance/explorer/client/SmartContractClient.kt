package io.provenance.explorer.client

import com.fasterxml.jackson.databind.JsonNode
import feign.Headers
import feign.Param
import feign.RequestLine
import io.provenance.explorer.model.Code
import io.provenance.explorer.model.CodeWithContractCount
import io.provenance.explorer.model.Contract
import io.provenance.explorer.model.base.PagedResults

object SmartContractRoutes {
    const val SMART_CONTRACT_V2 = "${BaseRoutes.V2_BASE}/smart_contract"
    const val SMART_CONTRACT_V3 = "${BaseRoutes.V3_BASE}/smart_contract"
    const val CODE_ALL = "$SMART_CONTRACT_V3/code"
    const val CODE = "$SMART_CONTRACT_V2/code/{id}"
    const val CODE_CONTRACTS = "$SMART_CONTRACT_V2/code/{id}/contracts"
    const val CONTRACT_ALL = "$SMART_CONTRACT_V3/contract"
    const val CONTRACT = "$SMART_CONTRACT_V2/contract/{contract}"
    const val CONTRACT_HISTORY = "$SMART_CONTRACT_V2/contract/{contract}/history"
    const val CONTRACT_LABELS = "$SMART_CONTRACT_V2/contract/labels"
}

@Headers(BaseClient.CT_JSON)
interface SmartContractClient : BaseClient {

    @RequestLine("GET ${SmartContractRoutes.CODE_ALL}")
    fun codeList(
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1,
        @Param("creator") creator: String? = null,
        @Param("has_contracts") hasContracts: Boolean? = null
    ): PagedResults<CodeWithContractCount>

    @RequestLine("GET ${SmartContractRoutes.CODE}")
    fun code(@Param("id") id: Int): Code

    @RequestLine("GET ${SmartContractRoutes.CODE_CONTRACTS}")
    fun contractsByCode(
        @Param("id") id: Int,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1,
        @Param("creator") creator: String? = null,
        @Param("admin") admin: String? = null
    ): PagedResults<Contract>

    @RequestLine("GET ${SmartContractRoutes.CONTRACT_ALL}")
    fun contractList(
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1,
        @Param("creator") creator: String? = null,
        @Param("admin") admin: String? = null,
        @Param("label") label: String? = null
    ): PagedResults<Contract>

    @RequestLine("GET ${SmartContractRoutes.CONTRACT}")
    fun contract(@Param("contract") contract: String): Contract

    @RequestLine("GET ${SmartContractRoutes.CONTRACT_HISTORY}")
    fun contractHistory(@Param("contract") contract: String): List<JsonNode>

    @RequestLine("GET ${SmartContractRoutes.CONTRACT_LABELS}")
    fun contractLabels(): List<String>
}
