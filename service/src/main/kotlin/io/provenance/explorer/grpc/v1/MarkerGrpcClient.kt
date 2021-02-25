package io.provenance.explorer.grpc.v1

import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.toMarker
import io.provenance.marker.v1.MarkerAccount
import io.provenance.marker.v1.QueryAllMarkersRequest
import io.provenance.marker.v1.QueryGrpc
import io.provenance.marker.v1.QueryHoldingRequest
import io.provenance.marker.v1.QueryHoldingResponse
import io.provenance.marker.v1.QueryMarkerRequest
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

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

    fun getAllMarkers(): List<MarkerAccount> =
        markerClient.allMarkers(QueryAllMarkersRequest.getDefaultInstance()).markersList.map { it.toMarker() }

    fun getMarkerDetail(id: String): MarkerAccount =
        markerClient.marker(QueryMarkerRequest.newBuilder().setId(id).build()).marker.toMarker()

    fun getMarkerHolders(denom: String): QueryHoldingResponse =
        markerClient.holding(QueryHoldingRequest.newBuilder().setId(denom).build())

}
