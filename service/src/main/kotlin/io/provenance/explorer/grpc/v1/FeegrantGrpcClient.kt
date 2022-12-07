package io.provenance.explorer.grpc.v1

import cosmos.base.query.v1beta1.pageResponse
import cosmos.feegrant.v1beta1.QueryGrpcKt
import cosmos.feegrant.v1beta1.queryAllowancesByGranterRequest
import cosmos.feegrant.v1beta1.queryAllowancesByGranterResponse
import cosmos.feegrant.v1beta1.queryAllowancesRequest
import cosmos.feegrant.v1beta1.queryAllowancesResponse
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.ExplorerProperties.Companion.UNDER_MAINTENANCE
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.getPagination
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class FeegrantGrpcClient(channelUri: URI) {

    private val feegrantClient: QueryGrpcKt.QueryCoroutineStub

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

        feegrantClient = QueryGrpcKt.QueryCoroutineStub(channel)
    }

    suspend fun getAllowancesForGrantee(grantee: String, offset: Int, limit: Int) =
        if (UNDER_MAINTENANCE) queryAllowancesResponse { this.pagination = pageResponse { this.total = 0 } }
        else
            feegrantClient.allowances(
                queryAllowancesRequest {
                    this.grantee = grantee
                    this.pagination = getPagination(offset, limit)
                }
            )

    suspend fun getGrantsByGranter(granter: String, offset: Int, limit: Int) =
        if (UNDER_MAINTENANCE) queryAllowancesByGranterResponse { this.pagination = pageResponse { this.total = 0 } }
        else
            feegrantClient.allowancesByGranter(
                queryAllowancesByGranterRequest {
                    this.granter = granter
                    this.pagination = getPagination(offset, limit)
                }
            )
}
