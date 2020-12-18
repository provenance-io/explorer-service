package io.provenance.explorer.client

import com.fasterxml.jackson.databind.JsonNode
import feign.Param
import feign.RequestLine
import io.provenance.explorer.domain.JsonRpc
import io.provenance.explorer.domain.StatusResult
import io.provenance.explorer.domain.TendermintBlockchainResponse

interface TendermintClient {

    @RequestLine("GET /tx_search?query=\"tx.height>{height}\"&page={page}&per_page={count}&order_by=\"desc\"")
    fun getRecentTransactions(@Param("height") height: Int, @Param("page") page: Int, @Param("count") count: Int): JsonNode

    @RequestLine("GET /blockchain?maxHeight={maxHeight}")
    fun getBlockchain(@Param("maxHeight") maxHeight: Int): JsonRpc<TendermintBlockchainResponse>

    @RequestLine("GET /validators?height={height}")
    fun getValidators(@Param("height") height: Int): JsonNode

    @RequestLine("GET /tx?hash=0x{hash}")
    fun getTransaction(@Param("hash") hash: String): JsonNode

    @RequestLine("GET /status")
    fun getStatus(): JsonRpc<StatusResult>
}