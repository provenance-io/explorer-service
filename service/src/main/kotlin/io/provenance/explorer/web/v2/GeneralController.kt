package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.models.explorer.DateTruncGranularity
import io.provenance.explorer.domain.models.explorer.GasStatistics
import io.provenance.explorer.domain.models.explorer.Spotlight
import io.provenance.explorer.service.ExplorerService
import io.provenance.explorer.web.BaseController
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

@Validated
@RestController
@RequestMapping(path = ["/api/v2"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(value = "General controller", produces = "application/json", consumes = "application/json", tags = ["General"])
class GeneralController(private val explorerService: ExplorerService) : BaseController() {

    @ApiOperation("Returns spotlight statistics")
    @GetMapping("/spotlight")
    fun spotlight(): ResponseEntity<Spotlight> = ResponseEntity.ok(explorerService.getSpotlightStatistics())

    @ApiOperation("Returns gas statistics")
    @GetMapping("/gas/statistics")
    fun gasStatistics(@RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime,
                      @RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime,
                      @RequestParam(required = false) granularity: DateTruncGranularity?
    ) =
        ResponseEntity.ok(explorerService.getGasStatistics(fromDate, toDate, granularity))

    @ApiOperation("Returns the ID of the chain associated with the explorer instance")
    @GetMapping("/chain/id")
    fun getChainId(): ResponseEntity<String> = ResponseEntity.ok(explorerService.getChainId())
}
