package io.provenance.explorer.grpc.v1

import io.grpc.ManagedChannelBuilder
import io.provenance.attribute.v1.Attribute
import io.provenance.attribute.v1.QueryAttributesRequest
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.getPaginationBuilder
import io.provenance.name.v1.QueryReverseLookupRequest
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit
import io.provenance.attribute.v1.QueryGrpc as AttrQueryGrpc
import io.provenance.name.v1.QueryGrpc as NameQueryGrpc

@Component
class AttributeGrpcClient(channelUri : URI) {

    private val attrClient: AttrQueryGrpc.QueryBlockingStub
    private val nameClient: NameQueryGrpc.QueryBlockingStub

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

        attrClient = AttrQueryGrpc.newBlockingStub(channel)
        nameClient = NameQueryGrpc.newBlockingStub(channel)
    }

    fun getAllAttributesForAddress(address: String?): MutableList<Attribute> {
        if (address == null) return mutableListOf()
        var offset = 0
        val limit = 100

        val results = attrClient.attributes(
            QueryAttributesRequest.newBuilder()
                .setAccount(address)
                .setPagination(getPaginationBuilder(offset, limit))
                .build())

        val total = results.pagination?.total ?: results.attributesCount.toLong()
        val attributes = results.attributesList.toMutableList()

        while (attributes.count() < total) {
            offset += limit
            attrClient.attributes(
                QueryAttributesRequest.newBuilder()
                    .setAccount(address)
                    .setPagination(getPaginationBuilder(offset, limit))
                    .build())
                .let { attributes.addAll(it.attributesList) }
        }

        return attributes
    }

    fun getNamesForAddress(address: String, offset: Int, limit: Int) =
        nameClient.reverseLookup(
            QueryReverseLookupRequest.newBuilder()
                .setAddress(address)
                .setPagination(getPaginationBuilder(offset, limit))
                .build()
        )
}
