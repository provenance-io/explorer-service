package io.provenance.explorer.model.download

data class ValidatorMetricData(
    val year: Int,
    val quarter: Int,
    val moniker: String,
    val operatorAddress: String,
    val isActive: Boolean,
    val isVerified: Boolean,
    val govVotes: Int,
    val govProposals: Int,
    val govPercentage: String,
    val blocksUp: Int,
    val blocksTotal: Int,
    val uptimePercentage: String
) {
    fun toCsv() =
        mutableListOf(
            this.year,
            this.quarter,
            this.moniker,
            this.operatorAddress,
            this.isActive,
            this.isVerified,
            this.govVotes,
            this.govProposals,
            this.govPercentage,
            this.blocksUp,
            this.blocksTotal,
            this.uptimePercentage
        )
}
