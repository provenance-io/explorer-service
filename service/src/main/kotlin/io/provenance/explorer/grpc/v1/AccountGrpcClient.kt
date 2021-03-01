package io.provenance.explorer.grpc.v1

import cosmos.bank.v1beta1.QueryOuterClass as BankOuterClass
import cosmos.auth.v1beta1.QueryOuterClass as AuthOuterClass
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit
import cosmos.bank.v1beta1.QueryGrpc as BankQueryGrpc
import cosmos.auth.v1beta1.QueryGrpc as AuthQueryGrpc

@Component
class AccountGrpcClient(channelUri : URI) {

    private val authClient: AuthQueryGrpc.QueryBlockingStub
    private val bankClient: BankQueryGrpc.QueryBlockingStub

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
    }

    fun getAccountInfo(address: String) =
        authClient.account(AuthOuterClass.QueryAccountRequest.newBuilder().setAddress(address).build()).account

    fun getAccountBalances(address: String) =
        bankClient.allBalances(BankOuterClass.QueryAllBalancesRequest.newBuilder().setAddress(address).build()).balancesList

    fun getSupplyByDenom(denom: String) =
        bankClient.supplyOf(BankOuterClass.QuerySupplyOfRequest.newBuilder().setDenom(denom).build())


}
