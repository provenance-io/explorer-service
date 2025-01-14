package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.annotation.HiddenApi
import io.provenance.explorer.model.base.DateTruncGranularity
import io.provenance.explorer.service.ExplorerService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.joda.time.DateTime
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v2"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "General Chain-related endpoints",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["General"]
)
class GeneralController(private val explorerService: ExplorerService) {

    @ApiOperation("Returns parameters for all modules on chain")
    @GetMapping("/params")
    fun param() = explorerService.getParams()

    @ApiOperation("Returns spotlight statistics")
    @GetMapping("/spotlight")
    @HiddenApi
    fun spotlight() = explorerService.getSpotlightStatistics()

    @ApiOperation("Returns gas statistics based on msg type (actual gas cost) - applies to single msg txs only")
    @GetMapping("/gas/stats")
    fun gasStats(
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = true
        )
        @RequestParam(required = true)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = true
        )
        @RequestParam(required = true)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime,
        @ApiParam(value = "The granularity of data, either DAY or HOUR", defaultValue = "DAY", required = false, allowableValues = "DAY,HOUR")
        @RequestParam(required = false)
        granularity: DateTruncGranularity?,
        @ApiParam(value = "The message type string, ie write_scope, send, add_attribute", required = false)
        @RequestParam(required = false)
        msgType: String?
    ) = explorerService.getGasStats(fromDate, toDate, granularity, msgType)

    @ApiOperation("Returns gas volume as processed through the chain")
    @GetMapping("/gas/volume")
    fun gasVolume(
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = true
        )
        @RequestParam(required = true)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = true
        )
        @RequestParam(required = true)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime,
        @ApiParam(
            value = "The granularity of data, either MONTH, DAY or HOUR",
            defaultValue = "DAY",
            required = false,
            allowableValues = "MONTH,DAY,HOUR"
        )
        @RequestParam(required = false)
        granularity: DateTruncGranularity?
    ) = explorerService.getGasVolume(fromDate, toDate, granularity)

    @ApiOperation("Returns the ID of the chain associated with the explorer instance")
    @GetMapping("/chain/id")
    fun getChainId() = explorerService.getChainId()

    @ApiOperation("Returns statistics on market rate for the chain for the given time period")
    @GetMapping("/chain/market_rate/period")
    fun getChainMarketRateStats(
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime?,
        @ApiParam(value = "The number of days of data returned", defaultValue = "14", required = false)
        @RequestParam(defaultValue = "14")
        @Min(1)
        dayCount: Int
    ) = explorerService.getChainMarketRateStats(fromDate, toDate, dayCount)

    @ApiOperation("Returns min/max/avg on market rate for the chain for given count of blocks with transactions")
    @GetMapping("/chain/market_rate")
    fun getChainMarketRateAvg(
        @ApiParam(value = "Block count between 1 and 2000", defaultValue = "500", required = false)
        @RequestParam(defaultValue = "500")
        @Min(1)
        @Max(2000)
        blockCount: Int
    ) = explorerService.getChainMarketRateAvg(blockCount)

    @ApiOperation("Returns a list of upgrades made against the chain")
    @GetMapping("/chain/upgrades")
    fun getChainUpgrades() = explorerService.getChainUpgrades()

    @ApiOperation("Returns a list of chain address prefixes")
    @GetMapping("/chain/prefixes")
    fun getChainPrefixes() = explorerService.getChainPrefixes()

    @ApiOperation("Returns a list of msg-based fees for the chain")
    @GetMapping("/chain/msg_based_fees")
    fun getChainMsgBasedFees() = explorerService.getMsgBasedFeeList()

    @ApiOperation("Returns the hourly AUM for the chain for the given time period")
    @GetMapping("/chain/aum/list")
    fun getChainAumSeries(
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime?,
        @ApiParam(value = "The number of days of data returned", defaultValue = "14", required = false)
        @RequestParam(defaultValue = "14")
        @Min(1)
        dayCount: Int
    ) = explorerService.getChainAumRecords(fromDate, toDate, dayCount)
}
