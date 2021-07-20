package io.provenance.explorer.grpc.v1

import cosmos.gov.v1beta1.QueryGrpc
import cosmos.gov.v1beta1.QueryOuterClass
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.domain.models.explorer.GovParamType
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class GovGrpcClient(channelUri: URI) {

    private val govClient: QueryGrpc.QueryBlockingStub

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

        govClient = QueryGrpc.newBlockingStub(channel)
    }

    fun getProposal(proposalId: Long) =
        govClient.proposal(QueryOuterClass.QueryProposalRequest.newBuilder().setProposalId(proposalId).build())

    fun getParams(param: GovParamType) =
        govClient.params(QueryOuterClass.QueryParamsRequest.newBuilder().setParamsType(param.name).build())

    fun getTally(proposalId: Long) =
        govClient.tallyResult(QueryOuterClass.QueryTallyResultRequest.newBuilder().setProposalId(proposalId).build())
}
