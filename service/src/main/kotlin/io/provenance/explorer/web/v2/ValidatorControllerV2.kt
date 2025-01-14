package io.provenance.explorer.web.v2

import io.provenance.explorer.model.ValidatorState
import io.provenance.explorer.model.base.Timeframe
import io.provenance.explorer.service.ExplorerService
import io.provenance.explorer.service.ValidatorService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.joda.time.DateTime
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v2/validators"], produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [org.springframework.http.MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "Validators",
    description = "Validator-related endpoints"
)
class ValidatorControllerV2(
    private val validatorService: ValidatorService,
    private val explorerService: ExplorerService
) {

    @Operation(summary = "Returns recent validators")
    @GetMapping("/recent")
    @Deprecated("Use /api/v3/validators/recent")
    @java.lang.Deprecated
    fun validators(
        @Parameter(description = "Record count between 1 and 50", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(50)
        count: Int,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(
            description = "Validator status - (active, candidate, jailed, all)",
            schema = Schema(defaultValue = "active"),
            required = false
        )
        @RequestParam(defaultValue = "active")
        status: String
    ) = validatorService.getRecentValidators(count, page, ValidatorState.valueOf(status.uppercase()))

    @Operation(summary = "Returns set of active validators at block height")
    @GetMapping("/height/{blockHeight}")
    fun validatorsAtHeight(
        @PathVariable blockHeight: Int,
        @Parameter(description = "Record count between 1 and 50", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(50)
        count: Int,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int
    ) = explorerService.getValidatorsAtHeight(blockHeight, count, page)

    @Operation(summary = "Returns all validators with an abbreviated data object")
    @GetMapping("/recent/abbrev")
    fun validatorsAllAbbrev() = validatorService.getAllValidatorsAbbrev()

    @Operation(summary = "Returns validator by operator, owning account, or consensus address")
    @GetMapping("/{address}")
    fun validator(
        @Parameter(description = "The Validator's operator, owning account, or consensus address")
        @PathVariable
        address: String
    ) = validatorService.getValidator(address)

    @Operation(summary = "Returns delegations for validator by operator address")
    @GetMapping("/{address}/delegations/bonded")
    fun validatorDelegationsBonded(
        @Parameter(description = "The Validator's operator, owning account, or consensus address")
        @PathVariable
        address: String,
        @Parameter(description = "Record count between 1 and 50", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(50)
        count: Int,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int
    ) = validatorService.getBondedDelegations(address, page, count)

    @Operation(summary = "Returns unbonding delegations for validator by operator address")
    @GetMapping("/{address}/delegations/unbonding")
    fun validatorDelegationsUnbonding(
        @Parameter(description = "The Validator's operator, owning account, or consensus address")
        @PathVariable
        address: String
    ) = validatorService.getUnbondingDelegations(address)

    @Operation(summary = "Returns commission info for validator by operator address")
    @GetMapping("/{address}/commission")
    fun validatorCommissionInfo(
        @Parameter(description = "The Validator's operator, owning account, or consensus address")
        @PathVariable
        address: String
    ) = validatorService.getCommissionInfo(address)

    @Operation(summary = "Returns commission history for validator by operator address")
    @GetMapping("/{address}/commission/history")
    fun validatorCommissionHistory(
        @Parameter(description = "The Validator's operator, owning account, or consensus address")
        @PathVariable
        address: String
    ) = validatorService.getCommissionRateHistory(address)

    @Operation(summary = "Returns min/max/avg on market rate for the validator for given last X transactions")
    @GetMapping("/{address}/market_rate")
    fun getValidatorMarketRateAvg(
        @Parameter(description = "The Validator's operator, owning account, or consensus address")
        @PathVariable
        address: String,
        @Parameter(description = "Transaction count between 1 and 2000", schema = Schema(defaultValue = "500"), required = false)
        @RequestParam(defaultValue = "500")
        @Min(1)
        @Max(2000)
        txCount: Int
    ) = validatorService.getValidatorMarketRateAvg(address, txCount)

    @Operation(summary = "Returns statistics on market rate for the address for the given time period")
    @GetMapping("/{address}/market_rate/period")
    fun validatorMarketRateStats(
        @Parameter(description = "The Validator's operator, owning account, or consensus address")
        @PathVariable
        address: String,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime?,
        @Parameter(description = "The number of days of data returned", schema = Schema(defaultValue = "14"), required = false)
        @RequestParam(defaultValue = "14")
        @Min(1)
        dayCount: Int
    ) = validatorService.getValidatorMarketRateStats(address, fromDate, toDate, dayCount)

    @Operation(summary = "Returns distinct validators with missed blocks for the timeframe")
    @GetMapping("/missed_blocks/distinct")
    fun missedBlocksDistinct(
        @Parameter(
            description = "The timeframe to calculate missed blocks. Corresponds to the most recent timeframe, ie. " +
                "HOUR = last hour of blocks, WEEK = last week of blocks",
            schema = Schema(defaultValue = "HOUR"),
            required = false
        )
        @RequestParam(defaultValue = "HOUR")
        timeframe: Timeframe
    ) = validatorService.getDistinctValidatorsWithMissedBlocksInTimeframe(timeframe)

    @Operation(summary = "Returns validators with missed blocks for the timeframe")
    @GetMapping("/missed_blocks")
    fun missedBlocks(
        @Parameter(
            description = "The timeframe to calculate missed blocks. Corresponds to the most recent timeframe, ie. " +
                "HOUR = last hour of blocks, WEEK = last week of blocks",
            schema = Schema(defaultValue = "HOUR"),
            required = false
        )
        @RequestParam(defaultValue = "HOUR")
        timeframe: Timeframe,
        @Parameter(description = "The Validator's operator, owning account, or consensus address", required = false)
        @RequestParam(required = false)
        validatorAddr: String?
    ) = validatorService.getMissedBlocksForValidatorInTimeframe(timeframe, validatorAddr)

    @Operation(summary = "Return uptime data for all active validators")
    @GetMapping("/uptime")
    fun uptimeData() = validatorService.activeValidatorUptimeStats()
}
