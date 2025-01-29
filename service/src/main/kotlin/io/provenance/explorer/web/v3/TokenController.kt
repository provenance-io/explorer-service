package io.provenance.explorer.web.v3

import com.fasterxml.jackson.databind.ObjectMapper
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.domain.annotation.HiddenApi
import io.provenance.explorer.domain.models.explorer.TokenHistoricalDataRequest
import io.provenance.explorer.service.TokenService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.beans.factory.annotation.Autowired
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
@RequestMapping(path = ["/api/v3/utility_token"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "Utility Token",
    description = "Utility Token-related data - statistics surrounding the utility token (nhash)"
)
class TokenController(private val tokenService: TokenService) {
    @Autowired
    lateinit var mapper: ObjectMapper

    @Operation(summary = "Returns token statistics for the chain, ie circulation, community pool")
    @GetMapping("/stats")
    fun getTokenStats() = tokenService.getTokenBreakdown()

    @Operation(summary = "Runs the distribution update")
    @GetMapping("/run")
    @HiddenApi
    fun runDistribution() = tokenService.updateTokenDistributionStats(UTILITY_TOKEN)

    @Operation(summary = "Returns distribution of hash between sets of accounts = all - nhash marker - zeroSeq - modules - contracts")
    @GetMapping("/distribution")
    fun getDistribution() = tokenService.getTokenDistributionStats()

    @Operation(summary = "Returns the top X accounts rich in 'nhash' = all - nhash marker - zeroSeq - modules - contracts")
    @GetMapping("/rich_list")
    fun getRichList(
        @RequestParam(defaultValue = "100")
        @Min(1)
        @Max(1000)
        limit: Int
    ) = tokenService.richList(limit)

    @Operation(summary = "Returns max supply of `nhash` = max")
    @GetMapping("/max_supply")
    fun getMaxSupply() = tokenService.maxSupply()

    @Operation(summary = "Returns total supply of `nhash` = max - burned ")
    @GetMapping("/total_supply")
    fun getTotalSupply() = tokenService.totalSupply()

    @Operation(summary = "Returns circulating supply of `nhash` = max - burned - modules - zeroSeq - pool - nonspendable ")
    @GetMapping("/circulating_supply")
    fun getCirculatingSupply() = tokenService.circulatingSupply()

    @Operation(summary = "Returns CoinMarketCap historical token pricing for the given dates inclusive")
    @GetMapping("/historical_pricing")
    fun getHistoricalPricing(
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
        toDate: LocalDate?
    ) = tokenService.getTokenHistorical(fromDate?.atStartOfDay(), toDate?.atStartOfDay())

    @Operation(summary = "Returns CoinMarketCap latest token pricing")
    @GetMapping("/latest_pricing")
    fun getLatestPricing() = tokenService.getTokenLatest()

    @Operation(summary = "Get Token Historical data as a ZIP download, containing CSVs")
    @GetMapping("/historical_pricing/download", produces = ["application/zip"])
    fun tokenHistoricalDownload(
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
        response: HttpServletResponse
    ) {
        val filters = TokenHistoricalDataRequest(fromDate?.atStartOfDay(), toDate?.atStartOfDay())
        response.status = HttpServletResponse.SC_OK
        response.addHeader("Content-Disposition", "attachment; filename=\"${filters.getFileNameBase()}.zip\"")
        tokenService.getHashPricingDataDownload(filters, response.outputStream)
    }
}
