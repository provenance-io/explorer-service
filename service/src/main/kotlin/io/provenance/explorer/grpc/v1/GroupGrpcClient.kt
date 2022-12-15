package io.provenance.explorer.grpc.v1

import cosmos.group.v1.QueryGrpcKt
import cosmos.group.v1.Types
import cosmos.group.v1.queryGroupInfoRequest
import cosmos.group.v1.queryGroupMembersRequest
import cosmos.group.v1.queryGroupPolicyInfoRequest
import cosmos.group.v1.queryProposalRequest
import cosmos.group.v1.queryTallyResultRequest
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.addBlockHeightToQuery
import io.provenance.explorer.grpc.extensions.getPagination
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class GroupGrpcClient(channelUri: URI) {

    private val groupClient: QueryGrpcKt.QueryCoroutineStub

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

        groupClient = QueryGrpcKt.QueryCoroutineStub(channel)
    }

    suspend fun getGroupByIdAtHeight(id: Long, height: Int) =
        try {
            groupClient
                .addBlockHeightToQuery(height.toString())
                .groupInfo(queryGroupInfoRequest { this.groupId = id })
        } catch (e: Exception) {
            null
        }

    suspend fun getMembersByGroupAtHeight(id: Long, height: Int) =
        try {
            var (offset, limit, total) = Triple(0, 300, 0)
            val memberList = mutableListOf<Types.Member>()

            do {
                groupClient
                    .addBlockHeightToQuery(height.toString())
                    .groupMembers(
                        queryGroupMembersRequest {
                            this.groupId = id
                            this.pagination = getPagination(offset, limit)
                        }
                    ).let { res ->
                        if (total == 0) {
                            total = res.pagination.total.toInt()
                        }
                        memberList.addAll(res.membersList.map { it.member })
                        offset += limit
                    }
            } while (memberList.size < total)
            memberList
        } catch (e: Exception) {
            mutableListOf()
        }

    suspend fun getPolicyByAddrAtHeight(policyAddr: String, height: Int) =
        try {
            groupClient
                .addBlockHeightToQuery(height.toString())
                .groupPolicyInfo(queryGroupPolicyInfoRequest { this.address = policyAddr })
        } catch (e: Exception) {
            null
        }

    suspend fun getProposalTallyAtHeight(proposalId: Long, height: Int) =
        try {
            groupClient
                .addBlockHeightToQuery(height.toString())
                .tallyResult(queryTallyResultRequest { this.proposalId = proposalId })
        } catch (e: Exception) {
            null
        }

    suspend fun getProposalAtHeight(proposalId: Long, height: Int) =
        try {
            groupClient
                .addBlockHeightToQuery(height.toString())
                .proposal(queryProposalRequest { this.proposalId = proposalId })
        } catch (e: Exception) {
            null
        }
}
