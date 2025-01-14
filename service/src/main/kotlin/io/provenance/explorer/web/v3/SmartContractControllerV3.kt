package io.provenance.explorer.web.v3

import io.provenance.explorer.service.SmartContractService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v3/smart_contract"], produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [org.springframework.http.MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "Smart Contract",
    description = "Smart Contract-related endpoints"
)
class SmartContractControllerV3(private val scService: SmartContractService) {

    @Operation(summary = "Returns a paginated list of smart contract codes, code ID descending")
    @GetMapping("/code")
    fun getCodesList(
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 50", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(50)
        count: Int,
        @Parameter(description = "Filter by the full address of the creator of a code", required = false)
        @RequestParam(required = false)
        creator: String?,
        @Parameter(
            name = "has_contracts",
            description = "Filter by whether the code has contracts associated with it",
            required = false
        )
        @RequestParam(name = "has_contracts", required = false)
        hasContracts: Boolean?
    ) = scService.getAllScCodesPaginated(page, count, creator, hasContracts)

    @Operation(summary = "Returns paginated list of smart contracts, creation block height descending")
    @GetMapping("/contract")
    fun getContractsList(
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 100", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(100)
        count: Int,
        @Parameter(description = "Filter by the full address of the creator of a contract", required = false)
        @RequestParam(required = false)
        creator: String?,
        @Parameter(description = "Filter by the full address of the admin of a contract", required = false)
        @RequestParam(required = false)
        admin: String?,
        @Parameter(description = "Filter by the label of a contract", required = false)
        @RequestParam(required = false)
        label: String?
    ) = scService.getAllScContractsPaginated(page, count, creator, admin, label)
}
