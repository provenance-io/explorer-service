package io.provenance.explorer.model

import io.provenance.explorer.model.base.CountTotal

data class BlockSummary(
    val height: Int,
    val hash: String,
    val time: String,
    val proposerAddress: String,
    val moniker: String,
    val icon: String?,
    val votingPower: CountTotal,
    val validatorCount: CountTotal,
    val txNum: Int
)
