package io.provenance.explorer.web.v2

import io.provenance.explorer.service.AccountService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v2/accounts"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Account-related endpoints - data for standard addresses",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Account"]
)
class AccountControllerV2(private val accountService: AccountService) {

    @ApiOperation("Returns account detail for the account address")
    @GetMapping("/{address}")
    fun getAccount(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String
    ) = accountService.getAccountDetail(address)

    @ApiOperation("Returns account balances for the account address")
    @GetMapping("/{address}/balances")
    @Deprecated("Use /api/v3/accounts/{address}/balances")
    @java.lang.Deprecated
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
    ) = ResponseEntity.ok(accountService.getAccountBalances(address, page, count))

    @ApiOperation("Returns delegations for the account address")
    @GetMapping("/{address}/delegations")
    fun getAccountDelegations(
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
    ) = accountService.getDelegations(address, page, count)

    @ApiOperation("Returns delegations in the process of unbonding for the account address")
    @GetMapping("/{address}/unbonding")
    fun getAccountUnbondingDelegations(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String
    ) = accountService.getUnbondingDelegations(address)

    @ApiOperation("Returns redelegations for the ccount address")
    @GetMapping("/{address}/redelegations")
    fun getAccountRedelegations(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String
    ) = accountService.getRedelegations(address)

    @ApiOperation("Returns current unclaimed rewards for the account address")
    @GetMapping("/{address}/rewards")
    fun getAccountRewards(
        @ApiParam(value = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String
    ) = accountService.getRewards(address)
}
