package io.provenance.explorer.grpc.v1

import cosmos.base.tendermint.v1beta1.Query
import cosmos.base.tendermint.v1beta1.ServiceGrpc
import cosmos.distribution.v1beta1.QueryOuterClass as DistOuterClass
import cosmos.distribution.v1beta1.QueryGrpc as DistGrpc
import cosmos.staking.v1beta1.QueryGrpc as StakingGrpc
import cosmos.staking.v1beta1.QueryOuterClass as StakingOuterClass
import cosmos.slashing.v1beta1.QueryGrpc as SlashingGrpc
import cosmos.slashing.v1beta1.QueryOuterClass as SlashingOuterClass
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.getPaginationBuilder
import io.provenance.marker.v1.QueryGrpc
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

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

    fun getLatestValidators() =
        tmClient.getLatestValidatorSet(Query.GetLatestValidatorSetRequest.getDefaultInstance()).validatorsList

    fun getValidatorsAtHeight(height: Int) =
        tmClient.getValidatorSetByHeight(
            Query.GetValidatorSetByHeightRequest.newBuilder().setHeight(height.toLong()).build())

    fun getStakingValidators(status: String, offset: Int, limit: Int) =
        stakingClient.validators(
            StakingOuterClass.QueryValidatorsRequest.newBuilder()
                .setStatus(status)
                .setPagination(getPaginationBuilder(offset, limit)).build())
            .validatorsList

    fun getStakingValidator(address: String) =
        stakingClient.validator(
            StakingOuterClass.QueryValidatorRequest.newBuilder().setValidatorAddr(address).build()).validator

    fun getStakingValidatorDelegations(address: String) =
        stakingClient.validatorDelegations(
            StakingOuterClass.QueryValidatorDelegationsRequest.newBuilder().setValidatorAddr(address).build())

    fun getStakingValidatorUnbondingDels(address: String) =
        stakingClient.validatorUnbondingDelegations(StakingOuterClass.QueryValidatorUnbondingDelegationsRequest
            .newBuilder().setValidatorAddr(address).build()).unbondingResponsesList

    fun getDistributionCommissions(address: String) =
        distClient.validatorCommission(
            DistOuterClass.QueryValidatorCommissionRequest.newBuilder().setValidatorAddress(address).build()).commission

    fun getDistributionRewards(address: String) =
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
