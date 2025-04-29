package io.provenance.explorer.web.v3

import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.domain.annotation.HiddenApi
import io.provenance.explorer.domain.extensions.toTxBody
import io.provenance.explorer.domain.extensions.toTxMessageBody
import io.provenance.explorer.domain.models.explorer.download.TxHistoryDataRequest
import io.provenance.explorer.model.BankSendRequest
import io.provenance.explorer.model.TxMessageBody
import io.provenance.explorer.model.base.DateTruncGranularity
import io.provenance.explorer.model.base.PeriodInSeconds
import io.provenance.explorer.service.AccountService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Validated
@RestController
@RequestMapping(path = ["/api/v3/accounts"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "Account",
    description = "Account-related endpoints - data for standard addresses",
)
class AccountControllerV3(private val accountService: AccountService, private val printer: JsonFormat.Printer) {

    @Operation(summary = "Builds send transaction for submission to blockchain")
    @PostMapping("/send")
    fun createSend(
        @Parameter(description = "Data used to craft the Send msg type")
        @RequestBody
        request: BankSendRequest,
    ): TxMessageBody {
        return accountService.createSend(request).toTxBody().toTxMessageBody(printer)
    }

    @Operation(summary = "Returns account balances for the account address, broken down by spendable and locked")
    @GetMapping("/{address}/balances")
    fun getAccountBalances(
        @Parameter(description = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String,
        @Parameter(description = "Record count between 1 and 100", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(100)
        count: Int,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int
    ) = accountService.getAccountBalancesDetailed(address, page, count)

    @Operation(summary = "Returns a vesting account's details and vesting schedule")
    @GetMapping("/{address}/vesting")
    fun getVestingSchedule(
        @Parameter(description = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String,
        @Parameter(description = "The period selection for a ContinuousVestingAccount", schema = Schema(defaultValue = "DAY"), required = false)
        @RequestParam(defaultValue = "DAY")
        continuousPeriod: PeriodInSeconds
    ) = accountService.getVestingSchedule(address, continuousPeriod)

    @Operation(summary = "Returns account balances for the account address, broken down by spendable and locked")
    @GetMapping("/{address}/balances_at_height")
    @HiddenApi
    fun getAccountBalancesAtHeight(
        @Parameter(description = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String,
        @Parameter(description = "block height to search at") @RequestParam height: Int,
        @Parameter(description = "The marker denom, can be base or display", required = false)
        @RequestParam(required = false)
        denom: String?
    ) = accountService.getAccountBalancesAllAtHeight(address, height, denom)

    @Operation(summary = "Get Account Tx Feepayer History chart data")
    @GetMapping("{address}/tx_history")
    fun getAccountTxFeepayerHistory(
        @Parameter(description = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String,
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
        @Parameter(
            description = "The granularity of data, either MONTH, DAY or HOUR",
            schema = Schema(defaultValue = "DAY", allowableValues = arrayOf("MONTH", "DAY", "HOUR")),
            required = false,

        )
        @RequestParam(defaultValue = "DAY")
        granularity: DateTruncGranularity
    ) = accountService.getAccountTxHistoryChartData(address, TxHistoryDataRequest(fromDate?.atStartOfDay(), toDate?.atStartOfDay(), granularity))

    @Operation(summary = "Get account tx history chart data as a ZIP download, containing CSVs")
    @GetMapping("{address}/tx_history/download", produces = ["application/zip"])
    fun txHistoryDownload(
        @Parameter(description = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String,
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
        @Parameter(
            description = "The granularity of data, either MONTH, DAY or HOUR",
            schema = Schema(defaultValue = "DAY", allowableValues = arrayOf("MONTH", "DAY", "HOUR")),
            required = false,

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
        val filters = TxHistoryDataRequest(fromDate?.atStartOfDay(), toDate?.atStartOfDay(), granularity, advancedMetrics)
        response.status = HttpServletResponse.SC_OK
        response.addHeader("Content-Disposition", "attachment; filename=\"${filters.getFileNameBase(address)}.zip\"")
        accountService.getAccountTxHistoryChartDataDownload(filters, address, response.outputStream)
    }

    @Operation(summary = "Returns account balance for the given denom for the account address, broken down by spendable and locked")
    @GetMapping("/{address}/balances/{denom}")
    fun getAccountBalanceForDenom(
        @Parameter(description = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String,
        @Parameter(description = "The marker denom, can be base or display")
        @PathVariable
        denom: String
    ) = accountService.getAccountBalanceForDenomDetailed(address, denom)

    @Operation(summary = "Returns account balance for the utility token for the account address, broken down by spendable and locked")
    @GetMapping("/{address}/balances/utility_token")
    fun getAccountBalanceForUtilityToken(
        @Parameter(description = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String
    ) = accountService.getAccountBalanceForUtilityToken(address)

    @Operation(summary = "Returns standard flags about the account (ie, isContract, isVesting)")
    @GetMapping("/{address}/flags")
    fun getAccountFlags(
        @Parameter(description = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String
    ) = accountService.getAccountFlags(address)
}
