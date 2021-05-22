package io.provenance.explorer.grpc.v1

import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.getPaginationBuilder
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit
import cosmos.auth.v1beta1.QueryGrpc as AuthQueryGrpc
import cosmos.auth.v1beta1.QueryOuterClass as AuthOuterClass
import cosmos.bank.v1beta1.QueryGrpc as BankQueryGrpc
import cosmos.bank.v1beta1.QueryOuterClass as BankOuterClass
import cosmos.distribution.v1beta1.QueryGrpc as DistGrpc
import cosmos.distribution.v1beta1.QueryOuterClass as DistOuterClass
import cosmos.staking.v1beta1.QueryGrpc as StakingGrpc
import cosmos.staking.v1beta1.QueryOuterClass as StakingOuterClass

@Component
class AccountGrpcClient(channelUri : URI) {

    private val authClient: AuthQueryGrpc.QueryBlockingStub
    private val bankClient: BankQueryGrpc.QueryBlockingStub
    private val stakingClient: StakingGrpc.QueryBlockingStub
    private val distClient: DistGrpc.QueryBlockingStub

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

        authClient = AuthQueryGrpc.newBlockingStub(channel)
        bankClient = BankQueryGrpc.newBlockingStub(channel)
        stakingClient = StakingGrpc.newBlockingStub(channel)
        distClient = DistGrpc.newBlockingStub(channel)
    }

    fun getAccountInfo(address: String) =
        try {
            authClient.account(AuthOuterClass.QueryAccountRequest.newBuilder().setAddress(address).build()).account
        } catch (e: Exception) {
            null
        }

    fun getAccountBalances(address: String, offset: Int, limit: Int) =
        bankClient.allBalances(
            BankOuterClass.QueryAllBalancesRequest.newBuilder()
                .setAddress(address)
                .setPagination(getPaginationBuilder(offset, limit))
                .build())

    fun getCurrentSupply(denom: String) =
        bankClient.supplyOf(BankOuterClass.QuerySupplyOfRequest.newBuilder().setDenom(denom).build()).amount

    fun getDelegations(address: String, offset: Int, limit: Int) =
        try {
            stakingClient.delegatorDelegations(
                StakingOuterClass.QueryDelegatorDelegationsRequest.newBuilder()
                    .setDelegatorAddr(address)
                    .setPagination(getPaginationBuilder(offset, limit))
                    .build()
            )
        } catch (e: Exception) {
            StakingOuterClass.QueryDelegatorDelegationsResponse.getDefaultInstance()
        }

    fun getUnbondingDelegations(address: String, offset: Int, limit: Int) =
        stakingClient.delegatorUnbondingDelegations(
            StakingOuterClass.QueryDelegatorUnbondingDelegationsRequest.newBuilder()
                .setDelegatorAddr(address)
                .setPagination(getPaginationBuilder(offset, limit))
                .build())

    fun getRedelegations(address: String, offset: Int, limit: Int) =
        stakingClient.redelegations(
            StakingOuterClass.QueryRedelegationsRequest.newBuilder()
                .setDelegatorAddr(address)
                .setPagination(getPaginationBuilder(offset, limit))
                .build())

    fun getRewards(delAddr: String) =
        distClient.delegationTotalRewards(
            DistOuterClass.QueryDelegationTotalRewardsRequest.newBuilder()
                .setDelegatorAddress(delAddr)
                .build())

}
