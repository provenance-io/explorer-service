package io.provenance.explorer.web.v3

import io.provenance.explorer.domain.models.explorer.download.TxHistoryDataRequest
import io.provenance.explorer.model.base.DateTruncGranularity
import io.provenance.explorer.model.base.Timeframe
import io.provenance.explorer.service.TransactionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@Validated
@RestController
@RequestMapping(path = ["/api/v3/txs"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "Transactions",
    description = "Transaction endpoints",
)
class TransactionControllerV3(private val transactionService: TransactionService) {

    @Operation(summary = "Get Tx History chart data")
    @GetMapping("/history")
    fun txHistory(
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: LocalDateTime?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: LocalDateTime?,
        @Parameter(
            description = "The granularity of data, either MONTH, DAY or HOUR",
            schema = Schema(defaultValue = "DAY", allowableValues = arrayOf("MONTH", "DAY", "HOUR")),
            required = false
        )
        @RequestParam(defaultValue = "DAY")
        granularity: DateTruncGranularity
    ) = transactionService.getTxHistoryChartData(TxHistoryDataRequest(fromDate, toDate, granularity))

    @Operation(summary = "Get Tx History chart data as a ZIP download, containing CSVs")
    @GetMapping("/history/download", produces = ["application/zip"])
    fun txHistoryDownload(
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: LocalDateTime?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: LocalDateTime?,
        @Parameter(
            description = "The granularity of data, either MONTH, DAY or HOUR",
            schema = Schema(defaultValue = "DAY", allowableValues = arrayOf("MONTH", "DAY", "HOUR")),
            required = false
        )
        @RequestParam(defaultValue = "DAY", required = false)
        granularity: DateTruncGranularity,
        @Parameter(
            description = "Toggle to return advanced metrics; will return Tx Type and Fee Type metrics; defaulted to FALSE",
            required = false
        )
        @RequestParam(defaultValue = "false", required = false)
        advancedMetrics: Boolean,
        response: HttpServletResponse
    ) {
        val filters = TxHistoryDataRequest(fromDate, toDate, granularity, advancedMetrics)
        response.status = HttpServletResponse.SC_OK
        response.addHeader("Content-Disposition", "attachment; filename=\"${filters.getFileNameBase(null)}.zip\"")
        transactionService.getTxHistoryChartDataDownload(filters, response.outputStream)
    }

    @Operation(summary = "Return list of transaction types by tx hash")
    @GetMapping("/{hash}/types")
    fun txTypesByTxHash(
        @PathVariable hash: String,
        @Parameter(required = false)
        @RequestParam(required = false)
        blockHeight: Int? = null
    ) = transactionService.getTxTypesByTxHash(hash)

    @Operation(summary = "Returns a heatmap of transaction activity on chain")
    @GetMapping("/heatmap")
    fun txHeatmap(
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: LocalDateTime?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: LocalDateTime?,
        @Parameter(
            description = "The timeframe of data, either QUARTER, MONTH, WEEK, or FOREVER",
            schema = Schema(defaultValue = "FOREVER", allowableValues = arrayOf("FOREVER", "QUARTER", "MONTH", "WEEK")),
            required = false
        )
        @RequestParam(defaultValue = "FOREVER", required = false)
        timeframe: Timeframe
    ) = transactionService.getTxHeatmap(fromDate, toDate, timeframe)
}
