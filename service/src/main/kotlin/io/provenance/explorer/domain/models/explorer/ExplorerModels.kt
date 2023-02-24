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

data class BlockTimeSpread(
    val year: Int,
    val quarter: Int,
    val minHeight: Int,
    val maxHeight: Int,
    val minTimestamp: DateTime,
    val maxTimestamp: DateTime,
    val totalBlocks: Int
)
