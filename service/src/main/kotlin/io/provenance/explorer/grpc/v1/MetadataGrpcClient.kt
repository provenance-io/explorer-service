package io.provenance.explorer.grpc.v1

import cosmos.base.query.v1beta1.pageRequest
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.ExplorerProperties.Companion.UNDER_MAINTENANCE
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import io.provenance.explorer.domain.extensions.toByteString
import io.provenance.explorer.grpc.extensions.getPaginationNoCount
import io.provenance.metadata.v1.QueryGrpcKt.QueryCoroutineStub
import io.provenance.metadata.v1.contractSpecificationRequest
import io.provenance.metadata.v1.contractSpecificationResponse
import io.provenance.metadata.v1.ownershipRequest
import io.provenance.metadata.v1.ownershipResponse
import io.provenance.metadata.v1.queryParamsRequest
import io.provenance.metadata.v1.recordSpecificationRequest
import io.provenance.metadata.v1.recordSpecificationsForContractSpecificationRequest
import io.provenance.metadata.v1.scopeRequest
import io.provenance.metadata.v1.scopeSpecificationRequest
import io.provenance.metadata.v1.scopeSpecificationResponse
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class MetadataGrpcClient(channelUri: URI, private val semaphore: Semaphore) {

    private val metadataClient: QueryCoroutineStub

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

        metadataClient = QueryCoroutineStub(channel)
    }

    suspend fun getScopesByOwner(address: String, offset: Int = 0, limit: Int = 10) =
        if (UNDER_MAINTENANCE) ownershipResponse { }
        else
            semaphore.withPermit {
                metadataClient.ownership(
                    ownershipRequest {
                        this.address = address
                        this.pagination = getPaginationNoCount(offset, limit)
                    }
                )
            }

    suspend fun getScopesByOwnerTotal(address: String) =
        semaphore.withPermit {
            val limit = 300
            var nextKey = "".toByteString()
            var count = 0

            do {
                metadataClient.ownership(
                    ownershipRequest {
                        this.address = address
                        this.pagination = pageRequest {
                            this.limit = limit.toLong()
                            if (nextKey.toStringUtf8().isNotBlank())
                                this.key = nextKey
                        }
                    }
                ).let {
                    nextKey = it.pagination.nextKey
                    count += it.scopeUuidsCount
                }
            } while (nextKey.toStringUtf8().isNotBlank())

            count
        }

    suspend fun getScopeById(uuid: String, includeRecords: Boolean = false, includeSessions: Boolean = false) =
        if (UNDER_MAINTENANCE) null
        else
            metadataClient.scope(
                scopeRequest {
                    this.scopeId = uuid
                    this.includeRecords = includeRecords
                    this.includeSessions = includeSessions
                }
            )

    suspend fun getScopeSpecById(addr: String) =
        if (UNDER_MAINTENANCE) scopeSpecificationResponse { }
        else
            metadataClient.scopeSpecification(scopeSpecificationRequest { this.specificationId = addr })

    suspend fun getContractSpecById(addr: String, includeRecords: Boolean = false) =
        if (UNDER_MAINTENANCE) contractSpecificationResponse { }
        else
            metadataClient.contractSpecification(
                contractSpecificationRequest {
                    this.specificationId = addr
                    this.includeRecordSpecs = includeRecords
                }
            )

    suspend fun getRecordSpecById(addr: String) =
        if (UNDER_MAINTENANCE) recordSpecificationRequest { }
        else
            metadataClient.recordSpecification(recordSpecificationRequest { this.specificationId = addr })

    suspend fun getRecordSpecsForContractSpec(contractSpec: String) =
        semaphore.withPermit {
            metadataClient.recordSpecificationsForContractSpecification(
                recordSpecificationsForContractSpecificationRequest { this.specificationId = contractSpec }
            )
        }

    suspend fun getMetadataParams() = metadataClient.params(queryParamsRequest { })
}
