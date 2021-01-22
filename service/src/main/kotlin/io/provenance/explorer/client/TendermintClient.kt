package io.provenance.explorer.client

import feign.Param
import feign.RequestLine
import io.provenance.explorer.domain.models.clients.tendermint.JsonRpc
import io.provenance.explorer.domain.models.clients.tendermint.StatusResult
import io.provenance.explorer.domain.models.clients.tendermint.TendermintBlockchainResponse

interface TendermintClient {

    @RequestLine("GET /blockchain?maxHeight={maxHeight}")
    fun getBlockchain(@Param("maxHeight") maxHeight: Int): JsonRpc<TendermintBlockchainResponse>

    @RequestLine("GET /status")
    fun getStatus(): JsonRpc<StatusResult>
}
