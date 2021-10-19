package io.provenance.explorer.grpc.v1

import io.grpc.ManagedChannelBuilder
import io.provenance.attribute.v1.Attribute
import io.provenance.attribute.v1.queryAttributesRequest
import io.provenance.attribute.v1.queryParamsRequest
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.getPagination
import io.provenance.name.v1.queryReverseLookupRequest
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit
import io.provenance.attribute.v1.QueryGrpcKt as AttrQueryGrpc
import io.provenance.name.v1.QueryGrpcKt as NameQueryGrpc

@Component
class AttributeGrpcClient(channelUri: URI) {

    private val attrClient: AttrQueryGrpc.QueryCoroutineStub
    private val nameClient: NameQueryGrpc.QueryCoroutineStub

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

        attrClient = AttrQueryGrpc.QueryCoroutineStub(channel)
        nameClient = NameQueryGrpc.QueryCoroutineStub(channel)
    }

    suspend fun getAllAttributesForAddress(address: String?): MutableList<Attribute> {
        if (address == null) return mutableListOf()
        var offset = 0
        val limit = 100

        val results = attrClient.attributes(
            queryAttributesRequest {
                this.account = address
                this.pagination = getPagination(offset, limit)
            }
        )

        val total = results.pagination?.total ?: results.attributesCount.toLong()
        val attributes = results.attributesList.toMutableList()

        while (attributes.count() < total) {
            offset += limit
            attrClient.attributes(
                queryAttributesRequest {
                    this.account = address
                    this.pagination = getPagination(offset, limit)
                }
            )
                .let { attributes.addAll(it.attributesList) }
        }

        return attributes
    }

    suspend fun getNamesForAddress(address: String, offset: Int, limit: Int) =
        nameClient.reverseLookup(
            queryReverseLookupRequest {
                this.address = address
                this.pagination = getPagination(offset, limit)
            }
        )

    suspend fun getAttrParams() = attrClient.params(queryParamsRequest { })
    suspend fun getNameParams() = nameClient.params(io.provenance.name.v1.queryParamsRequest { })
}
