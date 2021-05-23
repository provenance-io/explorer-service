package io.provenance.explorer.grpc.v1

import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit
import ibc.applications.transfer.v1.QueryGrpc as TransferQueryGrpc
import ibc.applications.transfer.v1.QueryOuterClass as TransferOuterClass

@Component
class IbcGrpcClient(channelUri: URI) {

    private val ibcClient: TransferQueryGrpc.QueryBlockingStub

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

        ibcClient = TransferQueryGrpc.newBlockingStub(channel)
    }

    fun getDenomTrace(hash: String) =
        ibcClient.denomTrace(TransferOuterClass.QueryDenomTraceRequest.newBuilder().setHash(hash).build()).denomTrace


}
