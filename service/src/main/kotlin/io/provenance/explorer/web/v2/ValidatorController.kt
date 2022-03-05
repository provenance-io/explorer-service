package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.models.explorer.Timeframe
import io.provenance.explorer.service.ExplorerService
import io.provenance.explorer.service.ValidatorService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
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
import javax.validation.constraints.Max
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v2/validators"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Validator-related endpoints",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Validators"]
)
class ValidatorController(
    private val validatorService: ValidatorService,
    private val explorerService: ExplorerService
) {

    @ApiOperation("Returns recent validators")
    @GetMapping("/recent")
    fun validators(
        @ApiParam(value = "Record count between 1 and 50", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) count: Int,
        @ApiParam(defaultValue = "1", required = false) @RequestParam(defaultValue = "1") @Min(1) page: Int,
        @ApiParam(
            value = "Validator status - (active, candidate, jailed, all)",
            defaultValue = "active",
            required = false
        )
        @RequestParam(defaultValue = "active") status: String
    ) = ResponseEntity.ok(validatorService.getRecentValidators(count, page, status))

    @ApiOperation("Returns set of active validators at block height")
    @GetMapping("/height/{blockHeight}")
    fun validatorsAtHeight(
        @PathVariable blockHeight: Int,
        @ApiParam(value = "Record count between 1 and 50", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) count: Int,
        @ApiParam(defaultValue = "1", required = false) @RequestParam(defaultValue = "1") @Min(1) page: Int,
    ) = ResponseEntity.ok(explorerService.getValidatorsAtHeight(blockHeight, count, page))

    @ApiOperation("Returns all validators with an abbreviated data object")
    @GetMapping("/recent/abbrev")
    fun validatorsAllAbbrev() = ResponseEntity.ok(validatorService.getAllValidatorsAbbrev())

    @ApiOperation("Returns validator by operator address")
    @GetMapping("/{address}")
    fun validator(
        @ApiParam(value = "The Validator's operator address") @PathVariable address: String
    ) = ResponseEntity.ok(validatorService.getValidator(address))

    @ApiOperation("Returns delegations for validator by operator address")
    @GetMapping("/{address}/delegations/bonded")
    fun validatorDelegationsBonded(
        @ApiParam(value = "The Validator's operator address") @PathVariable address: String,
        @ApiParam(value = "Record count between 1 and 50", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) count: Int,
        @ApiParam(defaultValue = "1", required = false) @RequestParam(defaultValue = "1") @Min(1) page: Int,
    ) = ResponseEntity.ok(validatorService.getBondedDelegations(address, page, count))

    @ApiOperation("Returns unbonding delegations for validator by operator address")
    @GetMapping("/{address}/delegations/unbonding")
    fun validatorDelegationsUnbonding(
        @ApiParam(value = "The Validator's operator address") @PathVariable address: String
    ) = ResponseEntity.ok(validatorService.getUnbondingDelegations(address))

    @ApiOperation("Returns commission info for validator by operator address")
    @GetMapping("/{address}/commission")
    fun validatorCommissionInfo(@ApiParam(value = "The Validator's operator address") @PathVariable address: String) =
        ResponseEntity.ok(validatorService.getCommissionInfo(address))

    @ApiOperation("Returns min/max/avg on market rate for the validator for given last X transactions")
    @GetMapping("/{address}/market_rate")
    fun getValidatorMarketRateAvg(
        @ApiParam(value = "The Validator's operator address") @PathVariable address: String,
        @ApiParam(value = "Transaction count between 1 and 2000", defaultValue = "500", required = false)
        @RequestParam(defaultValue = "500") @Min(1) @Max(2000) txCount: Int
    ) = ResponseEntity.ok(validatorService.getValidatorMarketRateAvg(address, txCount))

    @ApiOperation("Returns statistics on market rate for the address for the given time period")
    @GetMapping("/{address}/market_rate/period")
    fun validatorMarketRateStats(
        @ApiParam(value = "The Validator's operator address") @PathVariable address: String,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        ) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        ) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime?,
        @ApiParam(value = "The number of days of data returned", defaultValue = "14", required = false)
        @RequestParam(defaultValue = "14") @Min(1) dayCount: Int
    ) = ResponseEntity.ok(validatorService.getValidatorMarketRateStats(address, fromDate, toDate, dayCount))

    @ApiOperation("Returns block latency data for the validator")
    @GetMapping("/{address}/latency")
    fun blockLatency(
        @ApiParam(value = "The Validator's operator address") @PathVariable address: String,
        @ApiParam(value = "The number of blocks of data returned", defaultValue = "100", required = false)
        @RequestParam(defaultValue = "100") blockCount: Int
    ) = ResponseEntity.ok(validatorService.getBlockLatencyData(address, blockCount))

    @ApiOperation("Returns distinct validators with missed blocks for the timeframe")
    @GetMapping("/missed_blocks/distinct")
    fun missedBlocksDistinct(
        @ApiParam(
            value = "The timeframe to calculate missed blocks. Corresponds to the most recent timeframe, ie. " +
                "HOUR = last hour of blocks, WEEK = last week of blocks",
            defaultValue = "HOUR",
            required = false
        ) @RequestParam(defaultValue = "HOUR") timeframe: Timeframe
    ) = ResponseEntity.ok(validatorService.getDistinctValidatorsWithMissedBlocksInTimeframe(timeframe))

    @ApiOperation("Returns validators with missed blocks for the timeframe")
    @GetMapping("/missed_blocks")
    fun missedBlocks(
        @ApiParam(
            value = "The timeframe to calculate missed blocks. Corresponds to the most recent timeframe, ie. " +
                "HOUR = last hour of blocks, WEEK = last week of blocks",
            defaultValue = "HOUR",
            required = false
        ) @RequestParam(defaultValue = "HOUR") timeframe: Timeframe,
        @ApiParam(value = "The Validator's operator address", required = false)
        @RequestParam(required = false) validatorAddr: String?,
    ) = ResponseEntity.ok(validatorService.getMissedBlocksForValidatorInTimeframe(timeframe, validatorAddr))

    @ApiOperation("Return uptime data for all active validators")
    @GetMapping("/uptime")
    fun uptimeData() = ResponseEntity.ok(validatorService.activeValidatorUptimeStats())
}
