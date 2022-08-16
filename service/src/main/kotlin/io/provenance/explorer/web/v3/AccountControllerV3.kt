package io.provenance.explorer.web.v3

import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.domain.annotation.HiddenApi
import io.provenance.explorer.domain.models.explorer.PeriodInSeconds
import io.provenance.explorer.service.AccountService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
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

    @ApiOperation("Returns account balances for the account address, broken down by spendable and locked")
    @GetMapping("/{address}/balances")
    fun getAccountBalances(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable address: String,
        @ApiParam(value = "Record count between 1 and 100", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) count: Int,
        @ApiParam(defaultValue = "1", required = false) @RequestParam(defaultValue = "1") @Min(1) page: Int
    ) = ResponseEntity.ok(accountService.getAccountBalancesDetailed(address, page, count))

    @ApiOperation("Returns a vesting account's details and vesting schedule")
    @GetMapping("/{address}/vesting")
    fun getVestingSchedule(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable address: String,
        @ApiParam(value = "The period selection for a ContinuousVestingAccount", defaultValue = "DAY", required = false)
        @RequestParam(defaultValue = "DAY") continuousPeriod: PeriodInSeconds
    ) = ResponseEntity.ok(accountService.getVestingSchedule(address, continuousPeriod))

    @ApiOperation("Returns account balances for the account address, broken down by spendable and locked")
    @GetMapping("/{address}/balances/{height}")
    @HiddenApi
    fun getAccountBalancesAtHeight(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable address: String,
        @ApiParam(value = "block height to search at") @PathVariable height: Int
    ) = ResponseEntity.ok(accountService.getAccountBalancesAllAtHeight(address, height))
}
