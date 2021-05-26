package io.provenance.explorer.web.v2

import io.provenance.explorer.service.AccountService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v2/accounts"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(value = "Account controller", produces = "application/json", consumes = "application/json", tags = ["Accounts"])
class AccountController(private val accountService: AccountService) {

    @ApiOperation("Returns account detail for account address")
    @GetMapping("/{address}")
    fun getAccount(@PathVariable address: String) =
        ResponseEntity.ok(accountService.getAccountDetail(address))

    @ApiOperation("Returns account balances for account address")
    @GetMapping("/{address}/balances")
    fun getAccountBalances(
        @PathVariable address: String,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int
    ) = ResponseEntity.ok(accountService.getAccountBalances(address, page, count))

    @ApiOperation("Returns delegations for account address")
    @GetMapping("/{address}/delegations")
    fun getAccountDelegations(
        @PathVariable address: String,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int
    ) =
        ResponseEntity.ok(accountService.getDelegations(address, page, count))

    @ApiOperation("Returns unbonding delegations for account address")
    @GetMapping("/{address}/unbonding")
    fun getAccountUnbondingDelegations(@PathVariable address: String) =
        ResponseEntity.ok(accountService.getUnbondingDelegations(address))

    @ApiOperation("Returns redelegations for account address")
    @GetMapping("/{address}/redelegations")
    fun getAccountRedelegations(@PathVariable address: String) =
        ResponseEntity.ok(accountService.getRedelegations(address))

    @ApiOperation("Returns total rewards for account address")
    @GetMapping("/{address}/rewards")
    fun getAccountRewards(@PathVariable address: String) =
        ResponseEntity.ok(accountService.getRewards(address))

}
