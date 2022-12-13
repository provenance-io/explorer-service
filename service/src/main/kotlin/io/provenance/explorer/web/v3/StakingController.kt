package io.provenance.explorer.web.v3

import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.config.interceptor.JwtInterceptor.Companion.X_ADDRESS
import io.provenance.explorer.domain.extensions.toTxBody
import io.provenance.explorer.domain.extensions.toTxMessageBody
import io.provenance.explorer.model.StakingCancelUnbondingRequest
import io.provenance.explorer.model.StakingDelegateRequest
import io.provenance.explorer.model.StakingRedelegateRequest
import io.provenance.explorer.model.StakingUndelegateRequest
import io.provenance.explorer.model.StakingWithdrawCommissionRequest
import io.provenance.explorer.model.StakingWithdrawRewardsRequest
import io.provenance.explorer.model.TxMessageBody
import io.provenance.explorer.service.StakingService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v3/staking"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Staking-related endpoints - V3",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Staking"]
)
class StakingController(private val stakingService: StakingService, private val printer: JsonFormat.Printer) {

    @ApiOperation(value = "Builds a delegate transaction for submission to blockchain")
    @PostMapping("/delegate")
    fun createDelegate(
        @ApiParam(value = "Data used to craft the Delegate msg type")
        @RequestBody request: StakingDelegateRequest,
        @ApiParam(hidden = true) @RequestAttribute(name = X_ADDRESS, required = true) xAddress: String
    ): TxMessageBody {
        if (xAddress != request.delegator)
            throw IllegalArgumentException("Unable to process create delegate; connected wallet does not match request")
        return stakingService.createDelegate(request).toTxBody().toTxMessageBody(printer)
    }

    @ApiOperation(value = "Builds a redelegate transaction for submission to blockchain")
    @PostMapping("/redelegate")
    fun createRedelegate(
        @ApiParam(value = "Data used to craft the BeginRedelegate msg type")
        @RequestBody request: StakingRedelegateRequest,
        @ApiParam(hidden = true) @RequestAttribute(name = X_ADDRESS, required = true) xAddress: String
    ): TxMessageBody {
        if (xAddress != request.delegator)
            throw IllegalArgumentException("Unable to process create redelegate; connected wallet does not match request")
        return stakingService.createRedelegate(request).toTxBody().toTxMessageBody(printer)
    }

    @ApiOperation(value = "Builds an undelegate transaction for submission to blockchain")
    @PostMapping("/undelegate")
    fun createUndelegate(
        @ApiParam(value = "Data used to craft the Undelegate msg type")
        @RequestBody request: StakingUndelegateRequest,
        @ApiParam(hidden = true) @RequestAttribute(name = X_ADDRESS, required = true) xAddress: String
    ): TxMessageBody {
        if (xAddress != request.delegator)
            throw IllegalArgumentException("Unable to process create undelegate; connected wallet does not match request")
        return stakingService.createUndelegate(request).toTxBody().toTxMessageBody(printer)
    }

    @ApiOperation(value = "Builds an withdraw rewards transaction for submission to blockchain")
    @PostMapping("/withdraw_rewards")
    fun createWithdrawRewards(
        @ApiParam(value = "Data used to craft the WithdrawDelegatorReward msg type")
        @RequestBody request: StakingWithdrawRewardsRequest,
        @ApiParam(hidden = true) @RequestAttribute(name = X_ADDRESS, required = true) xAddress: String
    ): TxMessageBody {
        if (xAddress != request.delegator)
            throw IllegalArgumentException("Unable to process create withdraw rewards; connected wallet does not match request")
        return stakingService.createWithdrawRewards(request).toTxBody().toTxMessageBody(printer)
    }

    @ApiOperation(value = "Builds an withdraw commission transaction for submission to blockchain")
    @PostMapping("/withdraw_commission")
    fun createWithdrawCommission(
        @ApiParam(value = "Data used to craft the WithdrawValidatorCommission msg type")
        @RequestBody request: StakingWithdrawCommissionRequest,
        @ApiParam(hidden = true) @RequestAttribute(name = X_ADDRESS, required = true) xAddress: String
    ): TxMessageBody {
        if (!stakingService.validateWithdrawCommission(request.validator, xAddress))
            throw IllegalArgumentException("Unable to process create withdraw commission; connected wallet does not match request")
        return stakingService.createWithdrawCommission(request).toTxBody().toTxMessageBody(printer)
    }

    @ApiOperation(value = "Builds a cancel unbonding delegation transaction for submission to blockchain")
    @PostMapping("/cancel_unbonding")
    fun createCancelUnbondingDelegation(
        @ApiParam(value = "Data used to craft the CancelUnbondingDelegation msg type")
        @RequestBody request: StakingCancelUnbondingRequest,
        @ApiParam(hidden = true) @RequestAttribute(name = X_ADDRESS, required = true) xAddress: String
    ): TxMessageBody {
        if (xAddress != request.delegator)
            throw IllegalArgumentException("Unable to process create cancel unbonding delegation; connected wallet does not match request")
        return stakingService.createCancelUnbonding(request).toTxBody().toTxMessageBody(printer)
    }
}
