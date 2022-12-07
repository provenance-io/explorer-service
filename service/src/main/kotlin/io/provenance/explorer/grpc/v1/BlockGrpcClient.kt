package io.provenance.explorer.grpc.v1

import cosmos.base.tendermint.v1beta1.ServiceGrpcKt
import cosmos.base.tendermint.v1beta1.getBlockByHeightRequest
import cosmos.base.tendermint.v1beta1.getLatestBlockRequest
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class BlockGrpcClient(channelUri: URI) {

    private val tmClient: ServiceGrpcKt.ServiceCoroutineStub

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

        tmClient = ServiceGrpcKt.ServiceCoroutineStub(channel)
    }

    suspend fun getBlockAtHeight(maxHeight: Int) =
        try {
            tmClient.getBlockByHeight(getBlockByHeightRequest { this.height = maxHeight.toLong() })
        } catch (e: Exception) {
            null
        }

    suspend fun getLatestBlock() =
        try {
            tmClient.getLatestBlock(getLatestBlockRequest { })
        } catch (e: Exception) {
            null
        }
}
