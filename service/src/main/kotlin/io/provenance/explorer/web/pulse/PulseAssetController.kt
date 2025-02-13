package io.provenance.explorer.web.pulse

import io.provenance.explorer.domain.models.explorer.pulse.ExchangeSummary
import io.provenance.explorer.domain.models.explorer.pulse.PulseAssetSummary
import io.provenance.explorer.service.PulseMetricService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(
    path = ["/api/pulse/asset"],
    produces = [MediaType.APPLICATION_JSON_VALUE]
)
@Tag(
    name = "Pulse Assets",
    description = "Pulse asset endpoint"
)
class PulseAssetController(private val pulseMetricService: PulseMetricService) {

    @Operation(summary = "Exchange-traded Asset Summaries")
    @GetMapping("/summary/list")
    fun getPulseAssetSummaries(@RequestParam(required = false) search: String?): List<PulseAssetSummary> =
        pulseMetricService.pulseAssetSummaries().filter {
            // this is a small list so we can get away with this
            search.isNullOrBlank() ||
                    it.name.contains(search, ignoreCase = true) ||
                    it.symbol.contains(search, ignoreCase = true) ||
                    it.display.contains(search, ignoreCase = true) ||
                    it.base.contains(search, ignoreCase = true) ||
                    it.description.contains(search, ignoreCase = true)
        }

    @Operation(summary = "Asset exchange module-based details")
    @GetMapping("/exchange/summary/denom/{denom}")
    fun getAssetExchangeSummary(@PathVariable denom: String): List<ExchangeSummary> =
        pulseMetricService.exchangeSummaries(denom)
}
