package io.provenance.explorer.web.v3

import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.domain.annotation.HiddenApi
import io.provenance.explorer.domain.models.explorer.TokenHistoricalDataRequest
import io.provenance.explorer.service.TokenService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.joda.time.DateTime
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletResponse
import javax.validation.constraints.Max
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v3/utility_token"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Utility Token-related data - statistics surrounding the utility token (nhash)",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Utility Token"]
)
class TokenController(private val tokenService: TokenService) {

    @ApiOperation("Returns token statistics for the chain, ie circulation, community pool")
    @GetMapping("/stats")
    fun getTokenStats() = ResponseEntity.ok(tokenService.getTokenBreakdown())

    @ApiOperation("Runs the distribution update")
    @GetMapping("/run")
    @HiddenApi
    fun runDistribution() = ResponseEntity.ok(tokenService.updateTokenDistributionStats(UTILITY_TOKEN))

    @ApiOperation("Returns distribution of hash between sets of accounts = all - nhash marker - zeroSeq - modules - contracts")
    @GetMapping("/distribution")
    fun getDistribution() = ResponseEntity.ok(tokenService.getTokenDistributionStats())

    @ApiOperation("Returns the top X accounts rich in 'nhash' = all - nhash marker - zeroSeq - modules - contracts")
    @GetMapping("/rich_list")
    fun getRichList(@RequestParam(defaultValue = "100") @Min(1) @Max(1000) limit: Int) =
        ResponseEntity.ok(tokenService.richList(limit))

    @ApiOperation("Returns max supply of `nhash` = max")
    @GetMapping("/max_supply")
    fun getMaxSupply() = ResponseEntity.ok(tokenService.maxSupply())

    @ApiOperation("Returns total supply of `nhash` = max - burned ")
    @GetMapping("/total_supply")
    fun getTotalSupply() = ResponseEntity.ok(tokenService.totalSupply())

    @ApiOperation("Returns circulating supply of `nhash` = max - burned - modules - zeroSeq - pool - nonspendable ")
    @GetMapping("/circulating_supply")
    fun getCirculatingSupply() = ResponseEntity.ok(tokenService.circulatingSupply())

    @ApiOperation("Returns CoinMarketCap historical token pricing for the given dates inclusive")
    @GetMapping("/historical_pricing")
    fun getHistoricalPricing(
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        ) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        ) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime?
    ) = ResponseEntity.ok(tokenService.getTokenHistorical(fromDate, toDate))

    @ApiOperation("Returns CoinMarketCap latest token pricing")
    @GetMapping("/latest_pricing")
    fun getLatestPricing() = ResponseEntity.ok(tokenService.getTokenLatest())

    @ApiOperation("Get Token Historical data as a ZIP download, containing CSVs")
    @GetMapping("/historical_pricing/download", produces = ["application/zip"])
    fun tokenHistoricalDownload(
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
        response: HttpServletResponse
    ) {
        val filters = TokenHistoricalDataRequest(fromDate, toDate)
        response.status = HttpServletResponse.SC_OK
        response.addHeader("Content-Disposition", "attachment; filename=\"${filters.getFileNameBase()}.zip\"")
        tokenService.getHashPricingDataDownload(filters, response.outputStream)
    }
}
