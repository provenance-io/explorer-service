package io.provenance.explorer.web.v3

import io.provenance.explorer.domain.models.explorer.TxHistoryDataRequest
import io.provenance.explorer.model.base.DateTruncGranularity
import io.provenance.explorer.service.TransactionService
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

@Validated
@RestController
@RequestMapping(path = ["/api/v3/txs"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Transaction endpoints",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Transactions"]
)
class TransactionControllerV3(private val transactionService: TransactionService) {

    @ApiOperation("Get Tx History chart data")
    @GetMapping("/history")
    fun txHistory(
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
        @ApiParam(
            value = "The granularity of data, either MONTH, DAY or HOUR",
            defaultValue = "DAY",
            required = false,
            allowableValues = "MONTH,DAY,HOUR"
        )
        @RequestParam(defaultValue = "DAY") granularity: DateTruncGranularity
    ) = ResponseEntity.ok(transactionService.getTxHistoryChartData(TxHistoryDataRequest(fromDate, toDate, granularity)))

    @ApiOperation("Get Tx History chart data as a ZIP download, containing CSVs")
    @GetMapping("/history/download", produces = ["application/zip"])
    fun txHistoryDownload(
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
        @ApiParam(
            value = "The granularity of data, either MONTH, DAY or HOUR",
            defaultValue = "DAY",
            required = false,
            allowableValues = "MONTH,DAY,HOUR"
        )
        @RequestParam(defaultValue = "DAY", required = false) granularity: DateTruncGranularity,
        @ApiParam(
            type = "Boolean",
            value = "Toggle to return advanced metrics; will return Tx Type and Fee Type metrics; defaulted to FALSE",
            required = false
        )
        @RequestParam(defaultValue = "false", required = false) advancedMetrics: Boolean,
        response: HttpServletResponse
    ) {
        val filters = TxHistoryDataRequest(fromDate, toDate, granularity, advancedMetrics)
        response.status = HttpServletResponse.SC_OK
        response.addHeader("Content-Disposition", "attachment; filename=\"${filters.getFileNameBase(null)}.zip\"")
        transactionService.getTxHistoryChartDataDownload(filters, response.outputStream)
    }
}
