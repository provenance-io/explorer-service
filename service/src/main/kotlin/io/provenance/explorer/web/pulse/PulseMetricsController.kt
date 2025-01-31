package io.provenance.explorer.web.pulse

import io.provenance.explorer.domain.models.explorer.pulse.AssetMetric
import io.provenance.explorer.domain.models.explorer.pulse.HashMetricType
import io.provenance.explorer.service.PulseService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(
    path = ["/api/pulse/metrics"],
    produces = [MediaType.APPLICATION_JSON_VALUE]
)
@Tag(
    name = "Pulse Metrics",
    description = "Pulse metrics endpoint for Hash, Market Cap, and other metrics"
)
class PulseMetricsController(private val pulseService: PulseService) {

    @Operation(summary = "Default base token (hash) metrics")
    @GetMapping("/hash/type/{type}")
    fun getHashMarketCap(@PathVariable type: HashMetricType): AssetMetric =
        pulseService.hashMetric(type)
}
