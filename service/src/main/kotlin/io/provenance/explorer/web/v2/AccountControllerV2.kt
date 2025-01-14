package io.provenance.explorer.web.v2

import io.provenance.explorer.service.AccountService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
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
@RequestMapping(path = ["/api/v2/accounts"], produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "AccountV2",
    description = "Account-related endpoints - data for standard addresses",
)
class AccountControllerV2(private val accountService: AccountService) {

    @Operation(summary = "Returns account detail for the account address")
    @GetMapping("/{address}")
    fun getAccount(
        @Parameter(description = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String
    ) = accountService.getAccountDetail(address)

    @Operation(summary = "Returns account balances for the account address")
    @GetMapping("/{address}/balances")
    @Deprecated("Use /api/v3/accounts/{address}/balances")
    @java.lang.Deprecated
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
    ) = ResponseEntity.ok(accountService.getAccountBalances(address, page, count))

    @Operation(summary = "Returns delegations for the account address")
    @GetMapping("/{address}/delegations")
    fun getAccountDelegations(
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
    ) = accountService.getDelegations(address, page, count)

    @Operation(summary = "Returns delegations in the process of unbonding for the account address")
    @GetMapping("/{address}/unbonding")
    fun getAccountUnbondingDelegations(
        @Parameter(description = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String
    ) = accountService.getUnbondingDelegations(address)

    @Operation(summary = "Returns redelegations for the ccount address")
    @GetMapping("/{address}/redelegations")
    fun getAccountRedelegations(
        @Parameter(description = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String
    ) = accountService.getRedelegations(address)

    @Operation(summary = "Returns current unclaimed rewards for the account address")
    @GetMapping("/{address}/rewards")
    fun getAccountRewards(
        @Parameter(description = "The address of the account, starting with the standard account prefix")
        @PathVariable
        address: String
    ) = accountService.getRewards(address)
}
