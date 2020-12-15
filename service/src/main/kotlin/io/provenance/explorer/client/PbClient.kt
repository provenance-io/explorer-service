package io.provenance.explorer.client

import com.fasterxml.jackson.databind.JsonNode
import feign.Headers
import feign.Param
import feign.RequestLine
import io.provenance.explorer.domain.PbResponse
import io.provenance.explorer.domain.PbTransaction
import io.provenance.explorer.domain.PbTxSearchResponse
import io.provenance.explorer.domain.SigningInfo
import io.provenance.pbc.clients.JsonRPC

interface PbClient {

    @RequestLine("GET /txs/{hash}")
    fun getTx(@Param("hash") hash: String): PbTransaction

    @RequestLine("GET /txs?tx.maxheight={maxHeight}&tx.minheight={minHeight}&page={page}&limit={limit}")
    fun getTxsByHeights(@Param("maxHeight") maxHeight: Int, @Param("minHeight") minHeight: Int, @Param("page") page: Int, @Param("limit") limit: Int): PbTxSearchResponse

    @RequestLine("GET /validatorsets/{height}")
    fun getValidatorsAtHeight(@Param("height") height: Int): JsonNode

    @RequestLine("GET /staking/pool")
    fun getStakingPool(): JsonNode

    @RequestLine("GET /staking/validators?status={status}&page={page}&limit={limit}")
    fun getStakingValidators(@Param("status") status: String, @Param("page") page: Int, @Param("limit") limit: Int): JsonNode

    @RequestLine("GET /staking/validators/{validatorAddress}")
    fun getStakingValidator(@Param("validatorAddress") validatorAddress: String): JsonNode

    @RequestLine("GET /slashing/signing_infos")
    fun getSlashingSigningInfo(): PbResponse<List<SigningInfo>>

}