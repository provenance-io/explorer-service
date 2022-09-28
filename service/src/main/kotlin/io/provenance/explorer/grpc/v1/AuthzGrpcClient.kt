package io.provenance.explorer.grpc.v1

import cosmos.authz.v1beta1.QueryGrpcKt
import cosmos.authz.v1beta1.queryGranteeGrantsRequest
import cosmos.authz.v1beta1.queryGranterGrantsRequest
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.getPagination
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class AuthzGrpcClient(channelUri: URI) {

    private val authzClient: QueryGrpcKt.QueryCoroutineStub

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

        authzClient = QueryGrpcKt.QueryCoroutineStub(channel)
    }

    suspend fun getGrantsForGranter(granter: String, offset: Int, limit: Int) =
        authzClient.granterGrants(
            queryGranterGrantsRequest {
                this.granter = granter
                this.pagination = getPagination(offset, limit)
            }
        )

    suspend fun getGrantsForGrantee(grantee: String, offset: Int, limit: Int) =
        authzClient.granteeGrants(
            queryGranteeGrantsRequest {
                this.grantee = grantee
                this.pagination = getPagination(offset, limit)
            }
        )
}
