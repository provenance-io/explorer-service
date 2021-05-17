package io.provenance.explorer.grpc.v1

import com.google.protobuf.ProtocolStringList
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.getPaginationBuilder
import io.provenance.metadata.v1.OwnershipRequest
import io.provenance.metadata.v1.QueryGrpc
import io.provenance.metadata.v1.ScopeRequest
import io.provenance.metadata.v1.ScopesAllRequest
import io.provenance.metadata.v1.ScopesAllResponse
import io.provenance.metadata.v1.ValueOwnershipRequest
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class MetadataGrpcClient(channelUri : URI) {

    private val metadataClient: QueryGrpc.QueryBlockingStub

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

        metadataClient = QueryGrpc.newBlockingStub(channel)
    }

    fun getAllScopes(offset: Int = 0, limit: Int = 100) =
        metadataClient.scopesAll(
            ScopesAllRequest.newBuilder()
                .setPagination(getPaginationBuilder(offset, limit))
                .build()
        )

    fun getScopesByOwner(address: String, offset: Int = 0, limit: Int = 100) =
        metadataClient.ownership(
            OwnershipRequest.newBuilder()
                .setAddress(address)
                .setPagination(getPaginationBuilder(offset, limit))
                .build())

    fun getScopeById(uuid: String) =
        metadataClient.scope(
            ScopeRequest.newBuilder()
                .setScopeId(uuid)
                .setIncludeRecords(true)
                .build())
}

