package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.annotation.HiddenApi
import io.provenance.explorer.model.base.DateTruncGranularity
import io.provenance.explorer.service.ExplorerService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Validated
@RestController
@RequestMapping(path = ["/api/v2"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "General",
    description = "General Chain-related endpoints"
)
class GeneralController(private val explorerService: ExplorerService) {

    @Operation(summary = "Returns parameters for all modules on chain")
    @GetMapping("/params")
    fun param() = explorerService.getParams()

    @Operation(summary = "Returns spotlight statistics")
    @GetMapping("/spotlight")
    @HiddenApi
    fun spotlight() = explorerService.getSpotlightStatistics()

    @Operation(summary = "Returns gas statistics based on msg type (actual gas cost) - applies to single msg txs only")
    @GetMapping("/gas/stats")
    fun gasStats(
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = true
        )
        @RequestParam(required = true)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: LocalDate,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = true
        )
        @RequestParam(required = true)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: LocalDate,
        @Parameter(description = "The granularity of data, either DAY or HOUR", schema = Schema(defaultValue = "DAY", allowableValues = arrayOf("DAY", "HOUR")), required = false)
        @RequestParam(required = false)
        granularity: DateTruncGranularity?,
        @Parameter(description = "The message type string, ie write_scope, send, add_attribute", required = false)
        @RequestParam(required = false)
        msgType: String?
    ) = explorerService.getGasStats(fromDate.atStartOfDay(), toDate.atStartOfDay(), granularity, msgType)

    @Operation(summary = "Returns gas volume as processed through the chain")
    @GetMapping("/gas/volume")
    fun gasVolume(
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = true
        )
        @RequestParam(required = true)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: LocalDate,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = true
        )
        @RequestParam(required = true)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: LocalDate,
        @Parameter(
            description = "The granularity of data, either MONTH, DAY or HOUR",
            schema = Schema(defaultValue = "DAY", allowableValues = arrayOf("MONTH", "DAY", "HOUR")),
            required = false,
        )
        @RequestParam(required = false)
        granularity: DateTruncGranularity?
    ) = explorerService.getGasVolume(fromDate.atStartOfDay(), toDate.atStartOfDay(), granularity)

    @Operation(summary = "Returns the ID of the chain associated with the explorer instance")
    @GetMapping("/chain/id")
    fun getChainId() = explorerService.getChainId()

    @Operation(summary = "Returns statistics on market rate for the chain for the given time period")
    @GetMapping("/chain/market_rate/period")
    fun getChainMarketRateStats(
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: LocalDate?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: LocalDate?,
        @Parameter(description = "The number of days of data returned", schema = Schema(defaultValue = "14"), required = false)
        @RequestParam(defaultValue = "14")
        @Min(1)
        dayCount: Int
    ) = explorerService.getChainMarketRateStats(fromDate?.atStartOfDay(), toDate?.atStartOfDay(), dayCount)

    @Operation(summary = "Returns min/max/avg on market rate for the chain for given count of blocks with transactions")
    @GetMapping("/chain/market_rate")
    fun getChainMarketRateAvg(
        @Parameter(description = "Block count between 1 and 2000", schema = Schema(defaultValue = "500"), required = false)
        @RequestParam(defaultValue = "500")
        @Min(1)
        @Max(2000)
        blockCount: Int
    ) = explorerService.getChainMarketRateAvg(blockCount)

    @Operation(summary = "Returns a list of upgrades made against the chain")
    @GetMapping("/chain/upgrades")
    fun getChainUpgrades() = explorerService.getChainUpgrades()

    @Operation(summary = "Returns a list of chain address prefixes")
    @GetMapping("/chain/prefixes")
    fun getChainPrefixes() = explorerService.getChainPrefixes()

    @Operation(summary = "Returns a list of msg-based fees for the chain")
    @GetMapping("/chain/msg_based_fees")
    fun getChainMsgBasedFees() = explorerService.getMsgBasedFeeList()

    @Operation(summary = "Returns the hourly AUM for the chain for the given time period")
    @GetMapping("/chain/aum/list")
    fun getChainAumSeries(
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: LocalDate?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: LocalDate?,
        @Parameter(description = "The number of days of data returned", schema = Schema(defaultValue = "14"), required = false)
        @RequestParam(defaultValue = "14")
        @Min(1)
        dayCount: Int
    ) = explorerService.getChainAumRecords(fromDate?.atStartOfDay(), toDate?.atStartOfDay(), dayCount)
}
