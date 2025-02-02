package io.provenance.explorer.web.pulse

import io.provenance.explorer.domain.models.explorer.pulse.PulseCacheType
import io.provenance.explorer.domain.models.explorer.pulse.PulseMetric
import io.provenance.explorer.service.PulseMetricService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(
    path = ["/api/pulse/metric"],
    produces = [MediaType.APPLICATION_JSON_VALUE]
)
@Tag(
    name = "Pulse Metrics",
    description = "Pulse metrics endpoint for Hash, Market Cap, and other metrics"
)
class PulseMetricController(private val pulseMetricService: PulseMetricService) {

    @Operation(summary = "Global metrics based on the given type")
    @GetMapping("/type/{type}")
    fun getPulseMetricsByType(@PathVariable type: PulseCacheType): PulseMetric =
        pulseMetricService.pulseMetric(type)
}
