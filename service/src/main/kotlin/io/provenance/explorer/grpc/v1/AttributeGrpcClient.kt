package io.provenance.explorer.grpc.v1

import cosmos.bank.v1beta1.QueryOuterClass as BankOuterClass
import cosmos.auth.v1beta1.QueryOuterClass as AuthOuterClass
import io.grpc.ManagedChannelBuilder
import io.provenance.attribute.v1.Attribute
import io.provenance.attribute.v1.QueryAttributesRequest
import io.provenance.attribute.v1.QueryGrpc
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.getPaginationBuilder
import io.provenance.marker.v1.Balance
import io.provenance.marker.v1.QueryHoldingRequest
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit
import cosmos.bank.v1beta1.QueryGrpc as BankQueryGrpc
import cosmos.auth.v1beta1.QueryGrpc as AuthQueryGrpc

@Component
class AttributeGrpcClient(channelUri : URI) {

    private val attrClient: QueryGrpc.QueryBlockingStub

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

        attrClient = QueryGrpc.newBlockingStub(channel)
    }

    fun getAllAttributesForAddress(address: String): MutableList<Attribute> {
        var offset = 0
        val limit = 100

        val results = attrClient.attributes(
            QueryAttributesRequest.newBuilder()
                .setAccount(address)
                .setPagination(getPaginationBuilder(offset, limit))
                .build())

        val total = results.pagination?.total ?: results.attributesCount.toLong()
        val holders = results.attributesList

        while (holders.count() < total) {
            offset += limit
            attrClient.attributes(
                QueryAttributesRequest.newBuilder()
                    .setAccount(address)
                    .setPagination(getPaginationBuilder(offset, limit))
                    .build())
                .let { holders.addAll(it.attributesList) }
        }

        return holders
    }

}
