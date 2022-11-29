package io.provenance.explorer.grpc.v1

import ibc.applications.transfer.v1.queryDenomTraceRequest
import ibc.applications.transfer.v1.queryEscrowAddressRequest
import ibc.applications.transfer.v1.queryParamsRequest
import ibc.core.channel.v1.queryChannelClientStateRequest
import ibc.core.channel.v1.queryChannelRequest
import ibc.core.client.v1.queryClientParamsRequest
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit
import ibc.applications.interchain_accounts.controller.v1.QueryGrpcKt as IcaControllerQueryGrpc
import ibc.applications.interchain_accounts.controller.v1.queryParamsRequest as icaControllerParamsRequest
import ibc.applications.interchain_accounts.host.v1.QueryGrpcKt as IcaHostQueryGrpc
import ibc.applications.interchain_accounts.host.v1.queryParamsRequest as icaHostParamsRequest
import ibc.applications.transfer.v1.QueryGrpcKt as TransferQueryGrpc
import ibc.core.channel.v1.QueryGrpcKt as ChannelQueryGrpc
import ibc.core.client.v1.QueryGrpcKt as ClientQueryGrpc
import ibc.core.connection.v1.QueryGrpcKt as ConnectionQueryGrpc

@Component
class IbcGrpcClient(channelUri: URI) {

    private val transferClient: TransferQueryGrpc.QueryCoroutineStub
    private val channelClient: ChannelQueryGrpc.QueryCoroutineStub
    private val clientClient: ClientQueryGrpc.QueryCoroutineStub
    private val connectionClient: ConnectionQueryGrpc.QueryCoroutineStub
    private val icaControllerClient: IcaControllerQueryGrpc.QueryCoroutineStub
    private val icaHostClient: IcaHostQueryGrpc.QueryCoroutineStub

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

        transferClient = TransferQueryGrpc.QueryCoroutineStub(channel)
        channelClient = ChannelQueryGrpc.QueryCoroutineStub(channel)
        clientClient = ClientQueryGrpc.QueryCoroutineStub(channel)
        connectionClient = ConnectionQueryGrpc.QueryCoroutineStub(channel)
        icaControllerClient = IcaControllerQueryGrpc.QueryCoroutineStub(channel)
        icaHostClient = IcaHostQueryGrpc.QueryCoroutineStub(channel)
    }

    suspend fun getDenomTrace(hash: String) =
        transferClient.denomTrace(
            queryDenomTraceRequest { this.hash = hash }
        ).denomTrace

    suspend fun getEscrowAddress(portId: String, channelId: String, hrpPrefix: String) = runBlocking {
        transferClient.escrowAddress(
            queryEscrowAddressRequest {
                this.portId = portId
                this.channelId = channelId
            }
        ).escrowAddress
    }

    suspend fun getChannel(port: String, channel: String) =
        channelClient.channel(
            queryChannelRequest {
                this.portId = port
                this.channelId = channel
            }
        )

    suspend fun getClientForChannel(port: String, channel: String) =
        channelClient.channelClientState(
            queryChannelClientStateRequest {
                this.portId = port
                this.channelId = channel
            }
        )

    suspend fun getTransferParams() = transferClient.params(queryParamsRequest { })

    suspend fun getClientParams() = clientClient.clientParams(queryClientParamsRequest { })

    suspend fun getIcaControllerParams() = icaControllerClient.params(icaControllerParamsRequest { })

    suspend fun getIcaHostParams() = icaHostClient.params(icaHostParamsRequest { })
}
