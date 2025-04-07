package io.provenance.explorer.grpc.v1

import cosmos.auth.v1beta1.queryModuleAccountByNameRequest
import cosmos.gov.v1.queryParamsRequest
import cosmos.gov.v1.queryProposalRequest
import cosmos.gov.v1.queryTallyResultRequest
import cosmos.upgrade.v1beta1.queryAppliedPlanRequest
import cosmos.upgrade.v1beta1.queryCurrentPlanRequest
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import io.provenance.explorer.domain.models.explorer.GovParamType
import io.provenance.explorer.grpc.extensions.addBlockHeightToQuery
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit
import cosmos.auth.v1beta1.QueryGrpcKt as AuthQueryGrpc
import cosmos.gov.v1.QueryGrpcKt as GovQueryGrpc
import cosmos.upgrade.v1beta1.QueryGrpcKt as UpgradeQueryGrpc

@Component
class GovGrpcClient(channelUri: URI) {

    private val govClient: GovQueryGrpc.QueryCoroutineStub
    private val upgradeClient: UpgradeQueryGrpc.QueryCoroutineStub
    private val authClient: AuthQueryGrpc.QueryCoroutineStub

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

        govClient = GovQueryGrpc.QueryCoroutineStub(channel)
        upgradeClient = UpgradeQueryGrpc.QueryCoroutineStub(channel)
        authClient = AuthQueryGrpc.QueryCoroutineStub(channel)
    }

    suspend fun getProposal(proposalId: Long) =
        try {
            govClient.proposal(queryProposalRequest { this.proposalId = proposalId })
        } catch (e: Exception) {
            null
        }

    suspend fun getParams(param: GovParamType) =
        govClient.params(queryParamsRequest { this.paramsType = param.name })

    suspend fun getParamsAtHeight(param: GovParamType, height: Int) =
        try {
            govClient
                .addBlockHeightToQuery(height)
                .params(queryParamsRequest { this.paramsType = param.name })
        } catch (e: Exception) {
            null
        }

    suspend fun getTally(proposalId: Long) =
        try {
            govClient.tallyResult(queryTallyResultRequest { this.proposalId = proposalId })
        } catch (e: Exception) {
            null
        }

    suspend fun getIfUpgradeApplied(planName: String) =
        upgradeClient.appliedPlan(queryAppliedPlanRequest { this.name = planName })

    suspend fun getIfUpgradeScheduled() =
        try {
            upgradeClient.currentPlan(queryCurrentPlanRequest { })
        } catch (e: Exception) {
            null
        }

    suspend fun getGovModuleAccount() = authClient.moduleAccountByName(queryModuleAccountByNameRequest { name = "gov" })
}
