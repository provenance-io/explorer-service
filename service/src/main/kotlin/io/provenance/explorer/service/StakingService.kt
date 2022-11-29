package io.provenance.explorer.service

import com.google.protobuf.Any
import cosmos.base.v1beta1.coin
import cosmos.distribution.v1beta1.msgWithdrawDelegatorReward
import cosmos.distribution.v1beta1.msgWithdrawValidatorCommission
import cosmos.staking.v1beta1.msgBeginRedelegate
import cosmos.staking.v1beta1.msgCancelUnbondingDelegation
import cosmos.staking.v1beta1.msgDelegate
import cosmos.staking.v1beta1.msgUndelegate
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.StakingValidatorCacheRecord
import io.provenance.explorer.domain.exceptions.InvalidArgumentException
import io.provenance.explorer.domain.exceptions.requireToMessage
import io.provenance.explorer.domain.exceptions.validate
import io.provenance.explorer.domain.extensions.isZero
import io.provenance.explorer.domain.extensions.pack
import io.provenance.explorer.domain.models.explorer.StakingCancelUnbondingRequest
import io.provenance.explorer.domain.models.explorer.StakingDelegateRequest
import io.provenance.explorer.domain.models.explorer.StakingRedelegateRequest
import io.provenance.explorer.domain.models.explorer.StakingUndelegateRequest
import io.provenance.explorer.domain.models.explorer.StakingWithdrawCommissionRequest
import io.provenance.explorer.domain.models.explorer.StakingWithdrawRewardsRequest
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class StakingService(
    private val accountService: AccountService,
    private val validatorService: ValidatorService,
    private val assetService: AssetService,
    private val blockService: BlockService
) {

    protected val logger = logger(StakingService::class)

    fun createDelegate(request: StakingDelegateRequest): Any {
        validate(
            accountService.validateAddress(request.delegator),
            validatorService.validateValidator(request.validator),
            assetService.validateDenom(request.amount.denom),
            requireToMessage(request.amount.amount.toLong() > 0L) { "Delegation amount must be greater than zero" }
        )
        return msgDelegate {
            delegatorAddress = request.delegator
            validatorAddress = request.validator
            amount = coin {
                denom = request.amount.denom
                amount = request.amount.amount
            }
        }.pack()
    }

    fun createRedelegate(request: StakingRedelegateRequest): Any {
        validate(
            accountService.validateAddress(request.delegator),
            validatorService.validateValidator(request.validatorSrc),
            validatorService.validateValidator(request.validatorDst),
            requireToMessage(request.validatorSrc != request.validatorDst) { "The destination validator must be different that the source validator" },
            assetService.validateDenom(request.amount.denom),
            requireToMessage(request.amount.amount.toLong() > 0L) { "Redelegation amount must be greater than zero" }
        )
        return msgBeginRedelegate {
            delegatorAddress = request.delegator
            validatorSrcAddress = request.validatorSrc
            validatorDstAddress = request.validatorDst
            amount = coin {
                denom = request.amount.denom
                amount = request.amount.amount
            }
        }.pack()
    }

    fun createUndelegate(request: StakingUndelegateRequest): Any {
        validate(
            accountService.validateAddress(request.delegator),
            validatorService.validateValidator(request.validator),
            assetService.validateDenom(request.amount.denom),
            requireToMessage(request.amount.amount.toLong() > 0L) { "Undelegation amount must be greater than zero" }
        )
        return msgUndelegate {
            delegatorAddress = request.delegator
            validatorAddress = request.validator
            amount = coin {
                denom = request.amount.denom
                amount = request.amount.amount
            }
        }.pack()
    }

    fun createCancelUnbonding(request: StakingCancelUnbondingRequest): Any {
        validate(
            accountService.validateAddress(request.delegator),
            validatorService.validateValidator(request.validator),
            assetService.validateDenom(request.amount.denom),
            requireToMessage(request.amount.amount.toLong() > 0L) { "Undelegation amount must be greater than zero" },
            requireToMessage(request.unbondingCreateHeight < blockService.getLatestBlockHeight()) { "Unbonding Creation height must be less than the current height" }
        )
        return msgCancelUnbondingDelegation {
            delegatorAddress = request.delegator
            validatorAddress = request.validator
            amount = coin {
                denom = request.amount.denom
                amount = request.amount.amount
            }
            creationHeight = request.unbondingCreateHeight.toLong()
        }.pack()
    }

    fun createWithdrawRewards(request: StakingWithdrawRewardsRequest): Any {
        validate(
            accountService.validateAddress(request.delegator),
            validatorService.validateValidator(request.validator)
        )
        return msgWithdrawDelegatorReward {
            delegatorAddress = request.delegator
            validatorAddress = request.validator
        }.pack()
    }

    fun createWithdrawCommission(request: StakingWithdrawCommissionRequest): Any {
        validate(
            requireToMessage(
                !validatorService.getValidatorCommission(request.validator).isZero()
            ) { "Must have Validator commissions to withdraw." }
        )
        return msgWithdrawValidatorCommission { validatorAddress = request.validator }.pack()
    }

    fun validateWithdrawCommission(validator: String, xAddress: String) = transaction {
        StakingValidatorCacheRecord.findByOperAddr(validator)?.let { it.accountAddress == xAddress }
            ?: throw InvalidArgumentException("Validator $validator does not exist")
    }
}
