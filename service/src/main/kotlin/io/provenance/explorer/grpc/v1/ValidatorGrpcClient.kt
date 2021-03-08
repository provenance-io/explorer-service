package io.provenance.explorer.grpc.v1

import cosmos.base.tendermint.v1beta1.Query
import cosmos.base.tendermint.v1beta1.ServiceGrpc
import cosmos.staking.v1beta1.Staking
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.getPaginationBuilder
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit
import cosmos.distribution.v1beta1.QueryGrpc as DistGrpc
import cosmos.distribution.v1beta1.QueryOuterClass as DistOuterClass
import cosmos.slashing.v1beta1.QueryGrpc as SlashingGrpc
import cosmos.slashing.v1beta1.QueryOuterClass as SlashingOuterClass
import cosmos.staking.v1beta1.QueryGrpc as StakingGrpc
import cosmos.staking.v1beta1.QueryOuterClass as StakingOuterClass

@Component
class ValidatorGrpcClient(channelUri: URI) {

    private val tmClient: ServiceGrpc.ServiceBlockingStub
    private val stakingClient: StakingGrpc.QueryBlockingStub
    private val distClient: DistGrpc.QueryBlockingStub
    private val slashingClient: SlashingGrpc.QueryBlockingStub

    init {
        val channel =
            ManagedChannelBuilder.forAddress(channelUri.host, channelUri.port)
                .also {
                    if (channelUri.scheme == "grpcs") {
                        it.useTransportSecurity()
                    } else {
                        it.usePlaintext()
                    }
                }
                .idleTimeout(60, TimeUnit.SECONDS)
                .keepAliveTime(10, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .intercept(GrpcLoggingInterceptor())
                .build()

        tmClient = ServiceGrpc.newBlockingStub(channel)
        stakingClient = StakingGrpc.newBlockingStub(channel)
        distClient = DistGrpc.newBlockingStub(channel)
        slashingClient = SlashingGrpc.newBlockingStub(channel)
    }

    fun getLatestValidators(): MutableList<Query.Validator> {
        var offset = 0
        val limit = 100

        val results = tmClient.getLatestValidatorSet(
            Query.GetLatestValidatorSetRequest.newBuilder()
                .setPagination(getPaginationBuilder(offset, limit))
                .build())
        val total = results.pagination?.total ?: results.validatorsCount.toLong()
        val validators = results.validatorsList

        while (validators.count() < total) {
            offset += limit
            tmClient.getLatestValidatorSet(
                Query.GetLatestValidatorSetRequest.newBuilder()
                    .setPagination(getPaginationBuilder(offset, limit))
                    .build())
                .let { validators.addAll(it.validatorsList) }
        }

        return validators
    }

    fun getValidatorsAtHeight(height: Int): Query.GetValidatorSetByHeightResponse {
        var offset = 0
        val limit = 100

        val results = tmClient.getValidatorSetByHeight(
            Query.GetValidatorSetByHeightRequest.newBuilder()
                .setHeight(height.toLong())
                .setPagination(getPaginationBuilder(offset, limit))
                .build())
        val total = results.pagination?.total ?: results.validatorsCount.toLong()
        val validators = results.validatorsList

        while (validators.count() < total) {
            offset += limit
            tmClient.getValidatorSetByHeight(
                Query.GetValidatorSetByHeightRequest.newBuilder()
                    .setHeight(height.toLong())
                    .setPagination(getPaginationBuilder(offset, limit))
                    .build())
                .let { validators.addAll(it.validatorsList) }
        }

        return Query.GetValidatorSetByHeightResponse.newBuilder()
            .setBlockHeight(height.toLong())
            .setPagination(results.pagination)
            .addAllValidators(validators)
            .build()
    }

    fun getStakingValidators(): MutableList<Staking.Validator> {
        var offset = 0
        val limit = 100

        val results = stakingClient.validators(StakingOuterClass.QueryValidatorsRequest.newBuilder()
            .setPagination(getPaginationBuilder(offset, limit))
            .build())

        val total = results.pagination?.total ?: results.validatorsCount.toLong()
        val validators = results.validatorsList

        while (validators.count() < total) {
            offset += limit
            stakingClient.validators(StakingOuterClass.QueryValidatorsRequest.newBuilder()
                .setPagination(getPaginationBuilder(offset, limit))
                .build())
                .let { validators.addAll(it.validatorsList) }
        }

        return validators
    }

    fun getStakingValidator(address: String) =
        stakingClient.validator(
            StakingOuterClass.QueryValidatorRequest.newBuilder().setValidatorAddr(address).build()).validator

    fun getStakingValidatorDelegations(address: String, offset: Int, limit: Int) =
        stakingClient.validatorDelegations(
            StakingOuterClass.QueryValidatorDelegationsRequest.newBuilder()
                .setValidatorAddr(address)
                .setPagination(getPaginationBuilder(offset, limit))
                .build())

    fun getValidatorSelfDelegations(valAddress: String, delAddress: String) =
        stakingClient.delegation(
            StakingOuterClass.QueryDelegationRequest.newBuilder()
                .setValidatorAddr(valAddress)
                .setDelegatorAddr(delAddress)
                .build())

    fun getStakingValidatorUnbondingDels(address: String, offset: Int, limit: Int) =
        stakingClient.validatorUnbondingDelegations(
            StakingOuterClass.QueryValidatorUnbondingDelegationsRequest.newBuilder()
                .setValidatorAddr(address)
                .setPagination(getPaginationBuilder(offset, limit))
                .build())

    fun getValidatorCommission(address: String) =
        distClient.validatorCommission(
            DistOuterClass.QueryValidatorCommissionRequest.newBuilder().setValidatorAddress(address).build()).commission

    fun getValidatorRewards(address: String) =
        distClient.validatorOutstandingRewards(
            DistOuterClass.QueryValidatorOutstandingRewardsRequest.newBuilder().setValidatorAddress(address).build())
            .rewards

    fun getDelegatorWithdrawalAddress(delegator: String) =
        distClient.delegatorWithdrawAddress(
            DistOuterClass.QueryDelegatorWithdrawAddressRequest.newBuilder().setDelegatorAddress(delegator).build())
            .withdrawAddress

    fun getSigningInfos() =
        slashingClient.signingInfos(SlashingOuterClass.QuerySigningInfosRequest.getDefaultInstance()).infoList
}
