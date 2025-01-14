package io.provenance.explorer.web.v3

import io.provenance.explorer.service.GrantService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v3/grants"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Authz grant and Feegrant endpoints",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Grant"]
)
class GrantsController(private val grantService: GrantService) {

    @ApiOperation("Returns a paginated list of authz grants granted to the address")
    @GetMapping("/authz/{address}/grantee")
    fun getGrantsForGrantee(
        @ApiParam(value = "The address of the grantee, starting with the standard account prefix")
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
    ) = grantService.getGrantsForGranteePaginated(address, page, count)

    @ApiOperation("Returns a paginated list of authz grants granted by the address")
    @GetMapping("/authz/{address}/granter")
    fun getGrantsForGranter(
        @ApiParam(value = "The address of the granter, starting with the standard account prefix")
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
    ) = grantService.getGrantsForGranterPaginated(address, page, count)

    @ApiOperation("Returns a paginated list of feegrant allowances granted to the address")
    @GetMapping("/feegrant/{address}/grantee")
    fun getAllowancesForGrantee(
        @ApiParam(value = "The address of the grantee, starting with the standard account prefix")
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
    ) = grantService.getAllowancesForGranteePaginated(address, page, count)

    @ApiOperation("Returns a paginated list of feegrant allowances granted by the address")
    @GetMapping("/feegrant/{address}/granter")
    fun getAllowancesByGranter(
        @ApiParam(value = "The address of the granter, starting with the standard account prefix")
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
    ) = grantService.getAllowancesByGranterPaginated(address, page, count)
}
