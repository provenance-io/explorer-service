package io.provenance.explorer.client

import com.fasterxml.jackson.databind.JsonNode
import feign.Param
import feign.RequestLine
import io.provenance.explorer.domain.*

interface PbClient {

    @RequestLine("GET /txs/{hash}")
    fun getTx(@Param("hash") hash: String): PbTransaction

    @RequestLine("GET /txs?tx.maxheight={maxHeight}&tx.minheight={minHeight}&page={page}&limit={limit}")
    fun getTxsByHeights(@Param("maxHeight") maxHeight: Int, @Param("minHeight") minHeight: Int, @Param("page") page: Int, @Param("limit") limit: Int): PbTxSearchResponse

    @RequestLine("GET /validatorsets/{height}")
    fun getValidatorsAtHeight(@Param("height") height: Int): PbResponse<PbValidatorsResponse>

    @RequestLine("GET /validatorsets/latest")
    fun getLatestValidators(): PbResponse<PbValidatorsResponse>

    @RequestLine("GET /staking/pool")
    fun getStakingPool(): JsonNode

    @RequestLine("GET /staking/validators?status={status}&page={page}&limit={limit}")
    fun getStakingValidators(@Param("status") status: String, @Param("page") page: Int, @Param("limit") limit: Int): PbResponse<List<PbStakingValidator>>

    @RequestLine("GET /staking/validators/{validatorAddress}")
    fun getStakingValidator(@Param("validatorAddress") validatorAddress: String): PbResponse<PbStakingValidator>

    @RequestLine("GET /staking/validators/{validatorAddress}/delegations")
    fun getStakingValidatorDelegations(@Param("validatorAddress") validatorAddress: String): PbResponse<List<PbDelegation>>

    @RequestLine("GET /slashing/signing_infos")
    fun getSlashingSigningInfo(): PbResponse<List<SigningInfo>>

    @RequestLine("GET /distribution/validators/{validatorAddress}")
    fun getValidatorDistribution(@Param("validatorAddress") validatorAddress: String): PbResponse<ValidatorDistribution>

    @RequestLine("GET /supply/total/{denomination}")
    fun getSupplyTotalByDenomination(@Param("denomination") denomination: String): DenomSupply

}
