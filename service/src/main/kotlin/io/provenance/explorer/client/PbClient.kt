package io.provenance.explorer.client

import feign.Param
import feign.RequestLine
import io.provenance.explorer.domain.models.clients.PbResponse
import io.provenance.explorer.domain.models.clients.pb.AccountBalancesPaged
import io.provenance.explorer.domain.models.clients.pb.BlockSingle
import io.provenance.explorer.domain.models.clients.pb.AccountSingle
import io.provenance.explorer.domain.models.clients.pb.MarkerDetailSingle
import io.provenance.explorer.domain.models.clients.pb.MarkerHolderPaged
import io.provenance.explorer.domain.models.clients.pb.MarkersPaged
import io.provenance.explorer.domain.models.clients.pb.PbDelegationsPaged
import io.provenance.explorer.domain.models.clients.pb.PbStakingPaged
import io.provenance.explorer.domain.models.clients.pb.PbStakingSingle
import io.provenance.explorer.domain.models.clients.pb.PbTransaction
import io.provenance.explorer.domain.models.clients.pb.PbTxSearchResponse
import io.provenance.explorer.domain.models.clients.pb.PbValidatorsResponse
import io.provenance.explorer.domain.models.clients.pb.SigningInfoPaged
import io.provenance.explorer.domain.models.clients.pb.Supply
import io.provenance.explorer.domain.models.clients.pb.TxPaged
import io.provenance.explorer.domain.models.clients.pb.TxSingle
import io.provenance.explorer.domain.models.clients.pb.ValidatorDistribution

interface PbClient {

    // Txns
    @RequestLine("GET /txs/{hash}")
    fun getTx(@Param("hash") hash: String): PbTransaction

    @RequestLine("GET /txs?tx.maxheight={maxHeight}&tx.minheight={minHeight}&page={page}&limit={limit}")
    fun getTxsByHeights(
        @Param("maxHeight") maxHeight: Int,
        @Param("minHeight") minHeight: Int,
        @Param("page") page: Int,
        @Param("limit") limit: Int
    ): PbTxSearchResponse

    @RequestLine("GET /cosmos/tx/v1beta1/txs/{hash}")
    fun getTxV2(@Param("hash") hash: String): TxSingle

    @RequestLine("GET /cosmos/tx/v1beta1/txs?events=message.action={event}&pagination.offset={offset}&pagination.limit={limit}")
    fun getTxsByEventsV2(
        @Param("event") event: String,
        @Param("offset") offset: Int,
        @Param("limit") limit: Int
    ): TxPaged


    // Validators
    @RequestLine("GET /cosmos/base/tendermint/v1beta1/validatorsets/{height}")
    fun getValidatorsAtHeight(@Param("height") height: Int): PbValidatorsResponse

    @RequestLine("GET /cosmos/base/tendermint/v1beta1/validatorsets/latest")
    fun getLatestValidators(): PbValidatorsResponse


    // Staking
    @RequestLine("GET /cosmos/staking/v1beta1/validators?status={status}&pagination.offset={offset}&pagination.limit={limit}")
    fun getStakingValidators(
        @Param("status") status: String,
        @Param("offset") offset: Int,
        @Param("limit") limit: Int
    ): PbStakingPaged

    @RequestLine("GET /cosmos/staking/v1beta1/validators/{validatorAddress}")
    fun getStakingValidator(@Param("validatorAddress") validatorAddress: String): PbStakingSingle

    @RequestLine("GET /cosmos/staking/v1beta1/validators/{validatorAddress}/delegations")
    fun getStakingValidatorDelegations(@Param("validatorAddress") validatorAddress: String): PbDelegationsPaged


    // Slashing
    @RequestLine("GET /cosmos/slashing/v1beta1/signing_infos")
    fun getSlashingSigningInfo(): SigningInfoPaged


    // Distribution
    @RequestLine("GET /distribution/validators/{validatorAddress}")
    fun getValidatorDistribution(@Param("validatorAddress") validatorAddress: String): PbResponse<ValidatorDistribution>


    // Supply
    @RequestLine("GET /cosmos/bank/v1beta1/supply/{denom}")
    fun getSupplyTotalByDenomination(@Param("denom") denom: String): Supply


    // Markers (Assets)

    // allows pagination queries
    @RequestLine("GET /provenance/marker/v1/all")
    fun getMarkers(): MarkersPaged

    // id = marker denom or marker address
    @RequestLine("GET /provenance/marker/v1/holding/{id}")
    fun getMarkerHolders(@Param("id") id: String): MarkerHolderPaged

    // id = marker denom or marker address
    @RequestLine("GET /provenance/marker/v1/detail/{id}")
    fun getMarkerDetail(@Param("id") id: String): MarkerDetailSingle


    // Account
    @RequestLine("GET /cosmos/auth/v1beta1/accounts/{address}")
    fun getAccountInfo(@Param("address") address: String): AccountSingle

    @RequestLine("GET /cosmos/bank/v1beta1/balances/{address}")
    fun getAccountBalances(@Param("address") address: String): AccountBalancesPaged


    // Block
    @RequestLine("GET /cosmos/base/tendermint/v1beta1/blocks/{block}")
    fun getBlock(@Param("block") block: String): BlockSingle

    @RequestLine("GET /cosmos/base/tendermint/v1beta1/blocks/latest")
    fun getLatestBlock(): BlockSingle


}
