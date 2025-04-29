package io.provenance.explorer.web.v3

import com.google.protobuf.util.JsonFormat
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
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v3/staking"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "Staking",
    description = "Staking-related endpoints - V3"
)
class StakingController(private val stakingService: StakingService, private val printer: JsonFormat.Printer) {

    @Operation(summary = "Builds a delegate transaction for submission to blockchain")
    @PostMapping("/delegate")
    fun createDelegate(
        @Parameter(description = "Data used to craft the Delegate msg type")
        @RequestBody
        request: StakingDelegateRequest
    ): TxMessageBody {
        return stakingService.createDelegate(request).toTxBody().toTxMessageBody(printer)
    }

    @Operation(summary = "Builds a redelegate transaction for submission to blockchain")
    @PostMapping("/redelegate")
    fun createRedelegate(
        @Parameter(description = "Data used to craft the BeginRedelegate msg type")
        @RequestBody
        request: StakingRedelegateRequest
    ): TxMessageBody {
        return stakingService.createRedelegate(request).toTxBody().toTxMessageBody(printer)
    }

    @Operation(summary = "Builds an undelegate transaction for submission to blockchain")
    @PostMapping("/undelegate")
    fun createUndelegate(
        @Parameter(description = "Data used to craft the Undelegate msg type")
        @RequestBody
        request: StakingUndelegateRequest,
    ): TxMessageBody {
        return stakingService.createUndelegate(request).toTxBody().toTxMessageBody(printer)
    }

    @Operation(summary = "Builds an withdraw rewards transaction for submission to blockchain")
    @PostMapping("/withdraw_rewards")
    fun createWithdrawRewards(
        @Parameter(description = "Data used to craft the WithdrawDelegatorReward msg type")
        @RequestBody
        request: StakingWithdrawRewardsRequest,
    ): TxMessageBody {
        return stakingService.createWithdrawRewards(request).toTxBody().toTxMessageBody(printer)
    }

    @Operation(summary = "Builds an withdraw commission transaction for submission to blockchain")
    @PostMapping("/withdraw_commission")
    fun createWithdrawCommission(
        @Parameter(description = "Data used to craft the WithdrawValidatorCommission msg type")
        @RequestBody
        request: StakingWithdrawCommissionRequest,
    ): TxMessageBody {
        return stakingService.createWithdrawCommission(request).toTxBody().toTxMessageBody(printer)
    }

    @Operation(summary = "Builds a cancel unbonding delegation transaction for submission to blockchain")
    @PostMapping("/cancel_unbonding")
    fun createCancelUnbondingDelegation(
        @Parameter(description = "Data used to craft the CancelUnbondingDelegation msg type")
        @RequestBody
        request: StakingCancelUnbondingRequest,
    ): TxMessageBody {
        return stakingService.createCancelUnbonding(request).toTxBody().toTxMessageBody(printer)
    }
}
