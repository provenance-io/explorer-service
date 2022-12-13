package io.provenance.explorer.domain.models.explorer

import cosmos.staking.v1beta1.Staking
import cosmos.staking.v1beta1.copy
import io.provenance.explorer.model.ValidatorMoniker
import io.provenance.explorer.model.ValidatorState
import java.math.BigDecimal

fun Staking.Validator.zeroOutValidatorObj() =
    this.copy {
        this.delegatorShares = "0"
        this.tokens = "0"
    }

data class CurrentValidatorState(
    val operatorAddrId: Int,
    val operatorAddress: String,
    val blockHeight: Int,
    val moniker: String,
    val status: String,
    val jailed: Boolean,
    val tokenCount: BigDecimal,
    val json: Staking.Validator,
    val accountAddr: String,
    val consensusAddr: String,
    val consensusPubKey: String,
    val currentState: ValidatorState,
    val commissionRate: BigDecimal,
    val removed: Boolean,
    val imageUrl: String?
)

data class MissedBlockPeriod(
    val validator: ValidatorMoniker,
    val blocks: List<Int>
)
