package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.models.explorer.DateTruncGranularity
import io.provenance.explorer.domain.models.explorer.Params
import io.provenance.explorer.domain.models.explorer.Spotlight
import io.provenance.explorer.service.ExplorerService
import io.provenance.explorer.service.TokenSupplyService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.joda.time.DateTime
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v2"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(value = "General controller", produces = "application/json", consumes = "application/json", tags = ["General"])
class GeneralController(
    private val explorerService: ExplorerService,
    private val tokenSupplyService: TokenSupplyService
) {

    @ApiOperation("Returns parameters for all the modules")
    @GetMapping("/params")
    fun param(): ResponseEntity<Params> = ResponseEntity.ok(explorerService.getParams())

    @ApiOperation("Returns spotlight statistics")
    @GetMapping("/spotlight")
    fun spotlight(): ResponseEntity<Spotlight> = ResponseEntity.ok(explorerService.getSpotlightStatistics())

    @Deprecated("This endpoint is not being utilized. Instead, use '/gas/stats'")
    @ApiOperation("Returns gas statistics", hidden = true)
    fun gasStatistics(
        @RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime,
        @RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime,
        @RequestParam(required = false) granularity: DateTruncGranularity?
    ) = ResponseEntity.ok(explorerService.getGasStatistics(fromDate, toDate, granularity))

    @ApiOperation("Returns gas statistics")
    @GetMapping("/gas/stats")
    fun gasStats(
        @RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime,
        @RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime,
        @RequestParam(required = false) granularity: DateTruncGranularity?
    ) = ResponseEntity.ok(explorerService.getGasStats(fromDate, toDate, granularity))

    @ApiOperation("Returns gas volume")
    @GetMapping("/gas/volume")
    fun gasVolume(
        @RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime,
        @RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime,
        @RequestParam(required = false) granularity: DateTruncGranularity?
    ) = ResponseEntity.ok(explorerService.getGasVolume(fromDate, toDate, granularity))

    @ApiOperation("Returns the ID of the chain associated with the explorer instance")
    @GetMapping("/chain/id")
    fun getChainId(): ResponseEntity<String> = ResponseEntity.ok(explorerService.getChainId())

    @ApiOperation("Returns statistics on min gas fees for the chain")
    @GetMapping("/gas/fees/statistics")
    fun getGasFeeStatistics(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime?,
        @RequestParam(required = false, defaultValue = "14") @Min(1) dayCount: Int
    ) = ResponseEntity.ok(explorerService.getGasFeeStatistics(fromDate, toDate, dayCount))

    @ApiOperation("Returns statistics on min gas fees for the chain")
    @GetMapping("/token/stats")
    fun getTokenStats() = ResponseEntity.ok(tokenSupplyService.getTokenStats())

    @ApiOperation("Returns a list of upgrades made against the chain")
    @GetMapping("/chain/upgrades")
    fun getChainUpgrades() = ResponseEntity.ok(explorerService.getChainUpgrades())
}
