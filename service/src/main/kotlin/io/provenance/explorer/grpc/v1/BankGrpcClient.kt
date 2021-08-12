package io.provenance.explorer.grpc.v1

import cosmos.staking.v1beta1.Staking
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.extensions.toDecCoin
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit
import cosmos.bank.v1beta1.QueryGrpc as BankQueryGrpc
import cosmos.bank.v1beta1.QueryOuterClass as BankOuterClass
import cosmos.distribution.v1beta1.QueryGrpc as DistQueryGrpc
import cosmos.distribution.v1beta1.QueryOuterClass as DistOuterClass
import cosmos.staking.v1beta1.QueryGrpc as StakingQueryGrpc
import cosmos.staking.v1beta1.QueryOuterClass as StakingOuterClass

@Component
class BankGrpcClient(channelUri: URI) {

    private val logger = logger(BankGrpcClient::class)

    private val bankClient: BankQueryGrpc.QueryBlockingStub
    private val distClient: DistQueryGrpc.QueryBlockingStub
    private val stakingClient: StakingQueryGrpc.QueryBlockingStub

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

        bankClient = BankQueryGrpc.newBlockingStub(channel)
        distClient = DistQueryGrpc.newBlockingStub(channel)
        stakingClient = StakingQueryGrpc.newBlockingStub(channel)
    }

    fun getCurrentSupply(denom: String): String =
        bankClient.supplyOf(BankOuterClass.QuerySupplyOfRequest.newBuilder().setDenom(denom).build()).amount.amount

    fun getCommunityPoolAmount(denom: String): String =
        distClient.communityPool(DistOuterClass.QueryCommunityPoolRequest.newBuilder().build()).poolList
            .filter { it.denom == denom }[0]?.amount!!.toDecCoin()

    fun getBondedAmount(): String =
        stakingClient.validators(
            StakingOuterClass.QueryValidatorsRequest.newBuilder()
                .setStatus(Staking.BondStatus.BOND_STATUS_BONDED.toString())
                .build()
        ).validatorsList.sumOf { it.tokens.toBigDecimal() }.toString()

    fun getUnBondedAmount(): String =
        stakingClient.validators(
            StakingOuterClass.QueryValidatorsRequest.newBuilder()
                .setStatus(Staking.BondStatus.BOND_STATUS_UNBONDED.toString())
                .build()
        ).validatorsList.sumOf { it.tokens.toBigDecimal() }.toString()

    fun getMarkerBalance(address: String, denom: String): String =
        bankClient.balance(
            BankOuterClass.QueryBalanceRequest.newBuilder().setAddress(address).setDenom(denom).build()
        ).balance.amount
}
