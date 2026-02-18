package io.provenance.explorer.web.pulse

import io.provenance.explorer.domain.models.explorer.pulse.MetricRangeType
import io.provenance.explorer.domain.models.explorer.pulse.MetricRangeTypeConverter
import io.provenance.explorer.domain.models.explorer.pulse.PulseCacheType
import io.provenance.explorer.domain.models.explorer.pulse.PulseMetric
import io.provenance.explorer.service.PulseMetricService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.WebDataBinder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.InitBinder
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

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

    @InitBinder
    fun initBinder(binder: WebDataBinder) {
        binder.registerCustomEditor(
            MetricRangeType::class.java,
            MetricRangeTypeConverter()
        )
    }

    @Operation(summary = "Global metrics based on the given type")
    @GetMapping("/type/{type}")
    fun getPulseMetricsByType(
        @PathVariable type: PulseCacheType,
                              @RequestParam range: MetricRangeType
    ): PulseMetric =
        pulseMetricService.pulseMetric(type = type, range = range)

    @Operation(summary = "Historical metrics for the given type and date range")
    @GetMapping("/type/{type}/history")
    fun getPulseMetricHistoryByType(
        @PathVariable type: PulseCacheType,
        @RequestParam fromDate: LocalDate,
        @RequestParam toDate: LocalDate
    ): Map<LocalDate, PulseMetric> =
        pulseMetricService.pulseMetricHistory(type = type, fromDate = fromDate, toDate = toDate)

    @Operation(summary = "Back fill all metrics to range")
    @PostMapping("/backfill")
    fun backFillAllMetrics(
        @RequestParam fromDate: LocalDate,
                           @RequestParam toDate: LocalDate,
                           @RequestParam types: List<PulseCacheType>,
                           @RequestParam(required = false) denom: String?
    ) =
        pulseMetricService.backFillAllMetrics(fromDate, toDate, types, denom)
}
