package io.provenance.explorer.web.pulse

import io.provenance.explorer.domain.models.explorer.pulse.PulseAssetSummary
import io.provenance.explorer.service.PulseMetricService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
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
    fun getPulseAssetSummaries(): List<PulseAssetSummary> =
        pulseMetricService.pulseAssetSummaries()
}
