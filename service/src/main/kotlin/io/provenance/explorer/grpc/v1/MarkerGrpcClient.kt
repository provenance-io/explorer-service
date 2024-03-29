package io.provenance.explorer.grpc.v1

import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.toMarker
import io.provenance.marker.v1.MarkerAccount
import io.provenance.marker.v1.QueryGrpcKt
import io.provenance.marker.v1.queryMarkerRequest
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit
import io.provenance.marker.v1.QueryParamsRequest as MarkerRequest

@Component
class MarkerGrpcClient(channelUri: URI, private val semaphore: Semaphore) {

    private val markerClient: QueryGrpcKt.QueryCoroutineStub
    private val markerClientFuture: QueryGrpcKt.QueryCoroutineStub

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
                .idleTimeout(5, TimeUnit.MINUTES)
                .keepAliveTime(60, TimeUnit.SECONDS)
                .keepAliveTimeout(20, TimeUnit.SECONDS)
                .intercept(GrpcLoggingInterceptor())
                .build()

        markerClient = QueryGrpcKt.QueryCoroutineStub(channel)
        markerClientFuture = QueryGrpcKt.QueryCoroutineStub(channel)
    }

    suspend fun getMarkerDetail(id: String): MarkerAccount? =
        semaphore.withPermit {
            try {
                markerClient.marker(queryMarkerRequest { this.id = id }).marker.toMarker()
            } catch (e: Exception) {
                null
            }
        }

    suspend fun getMarkerParams() = markerClient.params(MarkerRequest.newBuilder().build())
}
