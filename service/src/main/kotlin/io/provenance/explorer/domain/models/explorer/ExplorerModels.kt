package io.provenance.explorer.domain.models.explorer

import org.joda.time.DateTime
import java.math.BigDecimal

data class BlockProposer(
    var blockHeight: Int,
    var proposerOperatorAddress: String,
    var blockTimestamp: DateTime,
    var blockLatency: BigDecimal? = null
)

data class GithubReleaseData(
    val releaseVersion: String,
    val createdAt: String,
    val releaseUrl: String
)
