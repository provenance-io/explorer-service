package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.models.explorer.Timeframe
import io.provenance.explorer.service.ExplorerService
import io.provenance.explorer.service.ValidatorService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.joda.time.DateTime
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v2/validators"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    value = "Validator controller",
    produces = "application/json",
    consumes = "application/json",
    tags = ["Validators"]
)
class ValidatorController(private val validatorService: ValidatorService, private val explorerService: ExplorerService) {

    @ApiOperation("Returns recent validators")
    @GetMapping("/recent")
    fun validators(
        @RequestParam(required = false, defaultValue = "100") @Min(1) count: Int,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "active") status: String
    ) = ResponseEntity.ok(validatorService.getRecentValidators(count, page, status))

    @ApiOperation("Returns set of validators at block height")
    @GetMapping("/height/{blockHeight}")
    fun validatorsAtHeight(
        @PathVariable blockHeight: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int
    ) = ResponseEntity.ok(explorerService.getValidatorsAtHeight(blockHeight, count, page))

    @ApiOperation("Returns validator by address id")
    @GetMapping("/{id}")
    fun validator(@PathVariable id: String) = ResponseEntity.ok(validatorService.getValidator(id))

    @ApiOperation("Returns delegations for validator by address id")
    @GetMapping("/{id}/delegations/bonded")
    fun validatorDelegationsBonded(
        @PathVariable id: String,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int
    ) = ResponseEntity.ok(validatorService.getBondedDelegations(id, page, count))

    @ApiOperation("Returns unbonding delegations for validator by address id")
    @GetMapping("/{id}/delegations/unbonding")
    fun validatorDelegationsUnbonding(@PathVariable id: String) =
        ResponseEntity.ok(validatorService.getUnbondingDelegations(id))

    @ApiOperation("Returns commission info for validator by address id")
    @GetMapping("/{id}/commission")
    fun validatorCommissionInfo(@PathVariable id: String) =
        ResponseEntity.ok(validatorService.getCommissionInfo(id))

    @ApiOperation("Returns statistics on min gas fees for the address")
    @GetMapping("/{id}/gas_fees")
    fun validatorGasFees(
        @PathVariable id: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime?,
        @RequestParam(required = false, defaultValue = "14") @Min(1) dayCount: Int
    ) = ResponseEntity.ok(validatorService.getGasFeeStatistics(id, fromDate, toDate, dayCount))

    @ApiOperation("Returns block latency data for the validator")
    @GetMapping("/{id}/latency")
    fun blockLatency(
        @PathVariable id: String,
        @RequestParam(required = false, defaultValue = "100") blockCount: Int
    ) = ResponseEntity.ok(validatorService.getBlockLatencyData(id, blockCount))

    @ApiOperation("Returns distinct validators with missed blocks for the timeframe")
    @GetMapping("/missed_blocks/distinct")
    fun missedBlocksDistinct(
        @RequestParam(required = false, defaultValue = "HOUR") timeframe: Timeframe
    ) = ResponseEntity.ok(validatorService.getDistinctValidatorsWithMissedBlocksInTimeframe(timeframe))

    @ApiOperation("Returns validators with missed blocks for the timeframe")
    @GetMapping("/missed_blocks")
    fun missedBlocks(
        @RequestParam(required = false, defaultValue = "HOUR") timeframe: Timeframe,
        @RequestParam(required = false) validatorAddr: String?,
    ) = ResponseEntity.ok(validatorService.getMissedBlocksForValidatorInTimeframe(timeframe, validatorAddr))
}
