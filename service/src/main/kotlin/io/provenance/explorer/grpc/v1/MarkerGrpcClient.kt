package io.provenance.explorer.grpc.v1

import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.getPaginationBuilder
import io.provenance.explorer.grpc.extensions.toMarker
import io.provenance.marker.v1.MarkerAccount
import io.provenance.marker.v1.QueryGrpc
import io.provenance.marker.v1.QueryHoldingRequest
import io.provenance.marker.v1.QueryHoldingResponse
import io.provenance.marker.v1.QueryMarkerRequest
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit
import io.provenance.marker.v1.QueryParamsRequest as MarkerRequest

@Component
class MarkerGrpcClient(channelUri: URI) {

    private val markerClient: QueryGrpc.QueryBlockingStub

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

        markerClient = QueryGrpc.newBlockingStub(channel)
    }

    fun getMarkerDetail(id: String): MarkerAccount? =
        try {
            markerClient.marker(QueryMarkerRequest.newBuilder().setId(id).build()).marker.toMarker()
        } catch (e: Exception) {
            null
        }

    fun getMarkerHolders(denom: String, offset: Int, count: Int): QueryHoldingResponse =
        try {
            markerClient.holding(
                QueryHoldingRequest.newBuilder()
                    .setId(denom)
                    .setPagination(getPaginationBuilder(offset, count))
                    .build()
            )
        } catch (e: Exception) {
            QueryHoldingResponse.getDefaultInstance()
        }

    fun getAllMarkerHolders(denom: String): QueryHoldingResponse =
        try {
            markerClient.holding(
                QueryHoldingRequest.newBuilder()
                    .setId(denom)
                    .build()
            )
        } catch (e: Exception) {
            QueryHoldingResponse.getDefaultInstance()
        }

    fun getMarkerParams() = markerClient.params(MarkerRequest.newBuilder().build())
}
