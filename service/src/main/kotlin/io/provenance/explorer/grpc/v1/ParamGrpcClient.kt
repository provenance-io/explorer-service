package io.provenance.explorer.grpc.v1

import cosmos.base.abci.v1beta1.Abci
import cosmos.tx.v1beta1.ServiceGrpc
import cosmos.tx.v1beta1.ServiceOuterClass
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.exceptions.TendermintApiException
import io.provenance.explorer.grpc.extensions.getPaginationBuilder
import io.provenance.explorer.grpc.extensions.toMarker
import io.provenance.marker.v1.MarkerAccount
import io.provenance.marker.v1.QueryMarkerRequest
import io.provenance.marker.v1.QueryParamsResponse
import io.provenance.metadata.v1.QueryGrpc
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class ParamGrpcClient(channelUri: URI) {
    private val paramClient: QueryGrpc.QueryBlockingStub
    protected val logger = logger(ParamGrpcClient::class)

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

        paramClient = QueryGrpc.newBlockingStub(channel)
    }

    // This probably isn't correct... But somehow we want to get the types that we need parameters for
    fun getParams(types: Types): io.provenance.metadata.v1.QueryParamsResponse? =
        // Okay, how do I add mapping from params to description here?
        try {
            val params = // TODO: Not sure how to get the query params from types here
            paramClient.params(QueryParamsResponse.newBuilder().setParams(params).build())
        } catch (e: Exception) {
            null
        }



}