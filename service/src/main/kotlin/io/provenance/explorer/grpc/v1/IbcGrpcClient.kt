package io.provenance.explorer.grpc.v1

import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.getEscrowAccountAddress
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit
import ibc.applications.transfer.v1.QueryGrpc as TransferQueryGrpc
import ibc.applications.transfer.v1.QueryOuterClass as TransferOuterClass
import ibc.core.channel.v1.QueryGrpc as ChannelQueryGrpc
import ibc.core.channel.v1.QueryOuterClass as ChannelOuterClass
import ibc.core.client.v1.QueryGrpc as ClientQueryGrpc
import ibc.core.client.v1.QueryOuterClass as ClientOuterClass
import ibc.core.connection.v1.QueryGrpc as ConnectionQueryGrpc

@Component
class IbcGrpcClient(channelUri: URI) {

    private val transferClient: TransferQueryGrpc.QueryBlockingStub
    private val channelClient: ChannelQueryGrpc.QueryBlockingStub
    private val clientClient: ClientQueryGrpc.QueryBlockingStub
    private val connectionClient: ConnectionQueryGrpc.QueryBlockingStub

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

        transferClient = TransferQueryGrpc.newBlockingStub(channel)
        channelClient = ChannelQueryGrpc.newBlockingStub(channel)
        clientClient = ClientQueryGrpc.newBlockingStub(channel)
        connectionClient = ConnectionQueryGrpc.newBlockingStub(channel)
    }

    fun getDenomTrace(hash: String) =
        transferClient.denomTrace(TransferOuterClass.QueryDenomTraceRequest.newBuilder().setHash(hash).build()).denomTrace

    fun getEscrowAddress(portId: String, channelId: String, hrpPrefix: String) =
        getEscrowAccountAddress(portId, channelId, hrpPrefix)

    fun getChannel(port: String, channel: String) =
        channelClient.channel(
            ChannelOuterClass.QueryChannelRequest.newBuilder()
                .setPortId(port)
                .setChannelId(channel)
                .build()
        )

    fun getClientForChannel(port: String, channel: String) =
        channelClient.channelClientState(
            ChannelOuterClass.QueryChannelClientStateRequest.newBuilder()
                .setPortId(port)
                .setChannelId(channel)
                .build()
        )

    fun getTransferParams() = transferClient.params(TransferOuterClass.QueryParamsRequest.newBuilder().build())

    fun getClientParams() = clientClient.clientParams(ClientOuterClass.QueryClientParamsRequest.newBuilder().build())
}
