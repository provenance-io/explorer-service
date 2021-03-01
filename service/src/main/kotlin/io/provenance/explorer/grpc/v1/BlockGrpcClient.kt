package io.provenance.explorer.grpc.v1

import cosmos.base.tendermint.v1beta1.Query
import cosmos.base.tendermint.v1beta1.ServiceGrpc
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class BlockGrpcClient(channelUri : URI) {

    private val tmClient: ServiceGrpc.ServiceBlockingStub

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
    }

    fun getBlockAtHeight(maxHeight: Int) =
        tmClient.getBlockByHeight(Query.GetBlockByHeightRequest.newBuilder().setHeight(maxHeight.toLong()).build())

    fun getLatestBlock() = tmClient.getLatestBlock(Query.GetLatestBlockRequest.getDefaultInstance())



}
