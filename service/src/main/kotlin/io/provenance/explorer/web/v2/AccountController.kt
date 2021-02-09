package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.models.explorer.AccountDetail
import io.provenance.explorer.service.AccountService
import io.provenance.explorer.web.BaseController
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v2/accounts"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(value = "Account controller", produces = "application/json", consumes = "application/json", tags = ["Accounts"])
class AccountController(private val accountService: AccountService) : BaseController() {

    @ApiOperation("Returns account detail for account address")
    @GetMapping("/{address}")
    fun getAccount(@PathVariable address: String): ResponseEntity<AccountDetail> =
        ResponseEntity.ok(accountService.getAccountDetail(address))

}
