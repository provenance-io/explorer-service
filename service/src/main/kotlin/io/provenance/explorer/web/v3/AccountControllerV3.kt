package io.provenance.explorer.web.v3

import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.config.interceptor.JwtInterceptor
import io.provenance.explorer.domain.annotation.HiddenApi
import io.provenance.explorer.domain.extensions.toTxBody
import io.provenance.explorer.domain.extensions.toTxMessageBody
import io.provenance.explorer.domain.models.explorer.download.TxHistoryDataRequest
import io.provenance.explorer.model.BankSendRequest
import io.provenance.explorer.model.TxMessageBody
import io.provenance.explorer.model.base.DateTruncGranularity
import io.provenance.explorer.model.base.PeriodInSeconds
import io.provenance.explorer.service.AccountService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.joda.time.DateTime
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletResponse
import javax.validation.constraints.Max
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v3/accounts"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Account-related endpoints - data for standard addresses",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Account"]
)
class AccountControllerV3(private val accountService: AccountService, private val printer: JsonFormat.Printer) {

    @ApiOperation(value = "Builds send transaction for submission to blockchain")
    @PostMapping("/send")
    fun createSend(
        @ApiParam(value = "Data used to craft the Send msg type")
        @RequestBody
        request: BankSendRequest,
        @ApiParam(hidden = true)
        @RequestAttribute(name = JwtInterceptor.X_ADDRESS, required = true)
        xAddress: String
    ): TxMessageBody {
        if (xAddress != request.from) {
            throw IllegalArgumentException("Unable to process create send; connected wallet does not match request")
        }
        return accountService.createSend(request).toTxBody().toTxMessageBody(printer)
    }

    @ApiOperation("Returns account balances for the account address, broken down by spendable and locked")
    @GetMapping("/{address}/balances")
    fun getAccountBalances(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String,
        @ApiParam(value = "Record count between 1 and 100", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(100)
        count: Int,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int
    ) = accountService.getAccountBalancesDetailed(address, page, count)

    @ApiOperation("Returns a vesting account's details and vesting schedule")
    @GetMapping("/{address}/vesting")
    fun getVestingSchedule(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String,
        @ApiParam(value = "The period selection for a ContinuousVestingAccount", defaultValue = "DAY", required = false)
        @RequestParam(defaultValue = "DAY")
        continuousPeriod: PeriodInSeconds
    ) = accountService.getVestingSchedule(address, continuousPeriod)

    @ApiOperation("Returns account balances for the account address, broken down by spendable and locked")
    @GetMapping("/{address}/balances_at_height")
    @HiddenApi
    fun getAccountBalancesAtHeight(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String,
        @ApiParam(value = "block height to search at") @RequestParam height: Int,
        @ApiParam(value = "The marker denom, can be base or display", required = false)
        @RequestParam(required = false)
        denom: String?
    ) = accountService.getAccountBalancesAllAtHeight(address, height, denom)

    @ApiOperation("Get Account Tx Feepayer History chart data")
    @GetMapping("{address}/tx_history")
    fun getAccountTxFeepayerHistory(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime?,
        @ApiParam(
            value = "The granularity of data, either MONTH, DAY or HOUR",
            defaultValue = "DAY",
            required = false,
            allowableValues = "MONTH,DAY,HOUR"
        )
        @RequestParam(defaultValue = "DAY")
        granularity: DateTruncGranularity
    ) = accountService.getAccountTxHistoryChartData(address, TxHistoryDataRequest(fromDate, toDate, granularity))

    @ApiOperation("Get account tx history chart data as a ZIP download, containing CSVs")
    @GetMapping("{address}/tx_history/download", produces = ["application/zip"])
    fun txHistoryDownload(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime?,
        @ApiParam(
            value = "The granularity of data, either MONTH, DAY or HOUR",
            defaultValue = "DAY",
            required = false,
            allowableValues = "MONTH,DAY,HOUR"
        )
        @RequestParam(defaultValue = "DAY", required = false)
        granularity: DateTruncGranularity,
        @ApiParam(
            type = "Boolean",
            value = "Toggle to return advanced metrics; will return Tx Type and Fee Type metrics; defaulted to FALSE",
            required = false
        )
        @RequestParam(defaultValue = "false", required = false)
        advancedMetrics: Boolean,
        response: HttpServletResponse
    ) {
        val filters = TxHistoryDataRequest(fromDate, toDate, granularity, advancedMetrics)
        response.status = HttpServletResponse.SC_OK
        response.addHeader("Content-Disposition", "attachment; filename=\"${filters.getFileNameBase(address)}.zip\"")
        accountService.getAccountTxHistoryChartDataDownload(filters, address, response.outputStream)
    }

    @ApiOperation("Returns account balance for the given denom for the account address, broken down by spendable and locked")
    @GetMapping("/{address}/balances/{denom}")
    fun getAccountBalanceForDenom(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String,
        @ApiParam(value = "The marker denom, can be base or display")
        @PathVariable
        denom: String
    ) = accountService.getAccountBalanceForDenomDetailed(address, denom)

    @ApiOperation("Returns account balance for the utility token for the account address, broken down by spendable and locked")
    @GetMapping("/{address}/balances/utility_token")
    fun getAccountBalanceForUtilityToken(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String
    ) = accountService.getAccountBalanceForUtilityToken(address)

    @ApiOperation("Returns standard flags about the account (ie, isContract, isVesting)")
    @GetMapping("/{address}/flags")
    fun getAccountFlags(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String
    ) = accountService.getAccountFlags(address)
}
