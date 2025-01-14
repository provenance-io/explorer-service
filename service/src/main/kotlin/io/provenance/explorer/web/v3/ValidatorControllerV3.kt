package io.provenance.explorer.web.v3

import io.provenance.explorer.domain.models.explorer.download.ValidatorMetricsRequest
import io.provenance.explorer.model.ValidatorState
import io.provenance.explorer.service.MetricsService
import io.provenance.explorer.service.ValidatorService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v3/validators"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Validator-related endpoints",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Validators"]
)
class ValidatorControllerV3(
    private val validatorService: ValidatorService,
    private val metricsService: MetricsService
) {

    @ApiOperation("Returns recent validators")
    @GetMapping("/recent")
    fun validators(
        @ApiParam(value = "Record count between 1 and 50", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(50)
        count: Int,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Validator status", defaultValue = "ACTIVE", required = false)
        @RequestParam(defaultValue = "ACTIVE")
        status: ValidatorState
    ) = validatorService.getRecentValidators(count, page, status)

    @ApiOperation("Returns a validator's metrics for the given quarter that correlate with the Validator Delegation Program")
    @GetMapping("/{address}/metrics")
    fun metrics(
        @ApiParam(value = "The Validator's operator, owning account, or consensus address") @PathVariable address: String,
        @ApiParam(value = "The year for the metrics")
        @RequestParam
        year: Int,
        @ApiParam(value = "The quarter for the metrics")
        @RequestParam
        @Min(1)
        @Max(4)
        quarter: Int
    ) = metricsService.getValidatorMetrics(address, year, quarter)

    @ApiOperation("Returns a validator's known metric periods that correlate with the Validator Delegation Program")
    @GetMapping("/{address}/metrics/periods")
    fun metricPeriods(
        @ApiParam(value = "The Validator's operator, owning account, or consensus address") @PathVariable address: String
    ) = metricsService.getQuarters(address)

    @ApiOperation("Returns all known metric periods that correlate with the Validator Delegation Program")
    @GetMapping("/metrics/periods")
    fun allMetricPeriods() = metricsService.getAllQuarters()

    @ApiOperation(
        "Downloads validators' metrics for the given quarter that correlate with the Validator Delegation Program"
    )
    @GetMapping("/metrics/download")
    fun metricsDownload(
        @ApiParam(value = "The year for the metrics")
        @RequestParam
        year: Int,
        @ApiParam(value = "The quarter for the metrics")
        @RequestParam
        @Min(1)
        @Max(4)
        quarter: Int,
        response: HttpServletResponse
    ) {
        val filters = ValidatorMetricsRequest(year, quarter)
        response.status = HttpServletResponse.SC_OK
        response.addHeader("Content-Disposition", "attachment; filename=\"${filters.getFilenameBase()}.zip\"")
        metricsService.downloadQuarterMetrics(filters, response.outputStream)
    }
}
