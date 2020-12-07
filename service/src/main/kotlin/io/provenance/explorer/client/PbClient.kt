package io.provenance.explorer.client

import com.fasterxml.jackson.databind.JsonNode
import feign.Headers
import feign.Param
import feign.RequestLine
import io.provenance.pbc.clients.JsonRPC

interface PbClient {

    @RequestLine("GET /txs/{hash}")
    fun getTx(@Param("hash") hash: String): JsonNode

    @RequestLine("GET /validatorsets/{height}")
    fun getValidatorsAtHeight(@Param("height") height: Int): JsonNode

    @RequestLine("GET /staking/pool")
    fun getStakingPool(): JsonNode

    @RequestLine("GET /staking/validators?status={status}&page={page}&limit={limit}")
    fun getStakingValidators(@Param("status") status: String, @Param("page") page: Int, @Param("limit") limit: Int): JsonNode

    @RequestLine("GET /staking/validators/{validatorAddress}")
    fun getStakingValidator(@Param("validatorAddress") validatorAddress: String): JsonNode

    @RequestLine("GET /slashing/signing_infos")
    fun getSlashingSigningInfo(): JsonNode

}