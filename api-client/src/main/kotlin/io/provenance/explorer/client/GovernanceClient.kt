package io.provenance.explorer.client

import feign.Headers
import feign.Param
import feign.RequestLine
import io.provenance.explorer.model.DepositRecord
import io.provenance.explorer.model.GovProposalDetail
import io.provenance.explorer.model.VoteRecord
import io.provenance.explorer.model.base.PagedResults

object GovernanceRoutes {
    const val GOV_V2 = "${BaseRoutes.V2_BASE}/gov"
    const val GOV_V3 = "${BaseRoutes.V3_BASE}/gov"
    const val PROPOSAL = "$GOV_V2/proposals/{id}"
    const val DEPOSITS = "$GOV_V2/proposals/{id}/deposits"
    const val ALL = "$GOV_V3/proposals"
    const val VOTES = "$GOV_V3/proposals/{id}/votes"
    const val VOTES_BY_ADDRESS = "$GOV_V3/votes/{address}"
}

@Headers(BaseClient.CT_JSON)
interface GovernanceClient : BaseClient {

    @RequestLine("GET ${GovernanceRoutes.ALL}")
    fun allProposals(
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<GovProposalDetail>

    @RequestLine("GET ${GovernanceRoutes.PROPOSAL}")
    fun proposal(@Param("id") id: Long): GovProposalDetail

    @RequestLine("GET ${GovernanceRoutes.DEPOSITS}")
    fun proposalDeposits(
        @Param("id") id: Long,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<DepositRecord>

    @RequestLine("GET ${GovernanceRoutes.VOTES}")
    fun proposalVotes(
        @Param("id") id: Long,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<VoteRecord>

    @RequestLine("GET ${GovernanceRoutes.VOTES_BY_ADDRESS}")
    fun votesByAddress(
        @Param("address") address: String,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<VoteRecord>
}
