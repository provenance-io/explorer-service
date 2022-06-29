package io.provenance.explorer.web.v2

import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.config.interceptor.JwtInterceptor
import io.provenance.explorer.domain.extensions.TxMessageBody
import io.provenance.explorer.domain.extensions.toTxBody
import io.provenance.explorer.domain.extensions.toTxMessageBody
import io.provenance.explorer.domain.models.explorer.BankSendRequest
import io.provenance.explorer.service.AccountService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.Max
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v2/accounts"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Account-related endpoints - data for standard addresses",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Account"]
)
class AccountController(private val accountService: AccountService, private val printer: JsonFormat.Printer) {

    @ApiOperation("Returns account detail for the account address")
    @GetMapping("/{address}")
    fun getAccount(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable address: String
    ) = ResponseEntity.ok(accountService.getAccountDetail(address))

    @ApiOperation("Returns account balances for the account address")
    @GetMapping("/{address}/balances")
    fun getAccountBalances(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable address: String,
        @ApiParam(value = "Record count between 1 and 100", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) count: Int,
        @ApiParam(defaultValue = "1", required = false) @RequestParam(defaultValue = "1") @Min(1) page: Int
    ) = ResponseEntity.ok(accountService.getAccountBalances(address, page, count))

    @ApiOperation("Returns delegations for the account address")
    @GetMapping("/{address}/delegations")
    fun getAccountDelegations(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable address: String,
        @ApiParam(value = "Record count between 1 and 100", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) count: Int,
        @ApiParam(defaultValue = "1", required = false) @RequestParam(defaultValue = "1") @Min(1) page: Int
    ) = ResponseEntity.ok(accountService.getDelegations(address, page, count))

    @ApiOperation("Returns delegations in the process of unbonding for the account address")
    @GetMapping("/{address}/unbonding")
    fun getAccountUnbondingDelegations(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable address: String
    ) = ResponseEntity.ok(accountService.getUnbondingDelegations(address))

    @ApiOperation("Returns redelegations for the ccount address")
    @GetMapping("/{address}/redelegations")
    fun getAccountRedelegations(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable address: String
    ) = ResponseEntity.ok(accountService.getRedelegations(address))

    @ApiOperation("Returns current unclaimed rewards for the account address")
    @GetMapping("/{address}/rewards")
    fun getAccountRewards(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable address: String
    ) = ResponseEntity.ok(accountService.getRewards(address))

    @ApiOperation("Returns attribute names owned by the account address")
    @GetMapping("/{address}/attributes/owned")
    fun getAccountNamesOwned(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable address: String,
        @ApiParam(value = "Record count between 1 and 50", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) count: Int,
        @ApiParam(defaultValue = "1", required = false) @RequestParam(defaultValue = "1") @Min(1) page: Int
    ) = ResponseEntity.ok(accountService.getNamesOwnedByAccount(address, page, count))

    @ApiOperation(value = "Builds send transaction for submission to blockchain")
    @PostMapping("/send")
    fun createSend(
        @ApiParam(value = "Data used to craft the Send msg type")
        @RequestBody request: BankSendRequest,
        @ApiParam(hidden = true) @RequestAttribute(name = JwtInterceptor.X_ADDRESS, required = true) xAddress: String
    ): TxMessageBody {
        if (xAddress != request.from)
            throw IllegalArgumentException("Unable to process create send; connected wallet does not match request")
        return accountService.createSend(request).toTxBody().toTxMessageBody(printer)
    }
}
