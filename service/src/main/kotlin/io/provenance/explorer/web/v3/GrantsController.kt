package io.provenance.explorer.web.v3

import io.provenance.explorer.service.GrantService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
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
@RequestMapping(path = ["/api/v3/grants"], produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [org.springframework.http.MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "Grant",
    description = "Authz grant and Feegrant endpoints"
)
class GrantsController(private val grantService: GrantService) {

    @Operation(summary = "Returns a paginated list of authz grants granted to the address")
    @GetMapping("/authz/{address}/grantee")
    fun getGrantsForGrantee(
        @Parameter(description = "The address of the grantee, starting with the standard account prefix")
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
    ) = grantService.getGrantsForGranteePaginated(address, page, count)

    @Operation(summary = "Returns a paginated list of authz grants granted by the address")
    @GetMapping("/authz/{address}/granter")
    fun getGrantsForGranter(
        @Parameter(description = "The address of the granter, starting with the standard account prefix")
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
    ) = grantService.getGrantsForGranterPaginated(address, page, count)

    @Operation(summary = "Returns a paginated list of feegrant allowances granted to the address")
    @GetMapping("/feegrant/{address}/grantee")
    fun getAllowancesForGrantee(
        @Parameter(description = "The address of the grantee, starting with the standard account prefix")
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
    ) = grantService.getAllowancesForGranteePaginated(address, page, count)

    @Operation(summary = "Returns a paginated list of feegrant allowances granted by the address")
    @GetMapping("/feegrant/{address}/granter")
    fun getAllowancesByGranter(
        @Parameter(description = "The address of the granter, starting with the standard account prefix")
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
    ) = grantService.getAllowancesByGranterPaginated(address, page, count)
}
