package io.provenance.explorer.domain.models.explorer

import java.math.BigDecimal
import java.time.LocalDateTime

data class BlockProposer(
    var blockHeight: Int,
    var proposerOperatorAddress: String,
    var blockTimestamp: LocalDateTime,
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
    val minTimestamp: LocalDateTime,
    val maxTimestamp: LocalDateTime,
    val totalBlocks: Int
)
