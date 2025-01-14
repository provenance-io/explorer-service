package io.provenance.explorer.web.v2

import io.provenance.explorer.service.SmartContractService
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
@RequestMapping(path = ["/api/v2/smart_contract"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Smart Contract-related endpoints",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Smart Contract"]
)
class SmartContractControllerV2(private val scService: SmartContractService) {

    @ApiOperation("Returns a paginated list of smart contract codes, code ID descending")
    @GetMapping("/codes/all")
    @Deprecated("Use /api/v3/smart_contract/code")
    @java.lang.Deprecated
    fun getCodesList(
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 50", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(50)
        count: Int,
        @ApiParam(value = "Filter by the full address of the creator of a code", required = false)
        @RequestParam(required = false)
        creator: String?,
        @ApiParam(
            name = "has_contracts",
            value = "Filter by whether the code has contracts associated with it",
            required = false
        )
        @RequestParam(name = "has_contracts", required = false)
        hasContracts: Boolean?
    ) = scService.getAllScCodesPaginated(page, count, creator, hasContracts)

    @ApiOperation("Returns a smart contract code object")
    @GetMapping("/code/{id}")
    fun getCode(
        @ApiParam(value = "The ID of the original code entry") @PathVariable id: Int
    ) = scService.getCode(id)

    @ApiOperation("Returns a paginated list of smart contracts by code ID, creation block height descending")
    @GetMapping("/code/{id}/contracts")
    fun getContractsByCode(
        @ApiParam(value = "The ID of the original code entry") @PathVariable id: Int,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 100", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(100)
        count: Int,
        @ApiParam(value = "Filter by the full address of the creator of a contract", required = false)
        @RequestParam(required = false)
        creator: String?,
        @ApiParam(value = "Filter by the full address of the admin of a contract", required = false)
        @RequestParam(required = false)
        admin: String?
    ) = scService.getContractsByCode(id, page, count, creator, admin)

    @ApiOperation("Returns paginated list of smart contracts, creation block height descending")
    @GetMapping("/contract/all")
    @Deprecated("Use /api/v3/smart_contract/contract")
    @java.lang.Deprecated
    fun getContractsList(
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 100", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(100)
        count: Int,
        @ApiParam(value = "Filter by the full address of the creator of a contract", required = false)
        @RequestParam(required = false)
        creator: String?,
        @ApiParam(value = "Filter by the full address of the admin of a contract", required = false)
        @RequestParam(required = false)
        admin: String?,
        @ApiParam(value = "Filter by the label of a contract", required = false)
        @RequestParam(required = false)
        label: String?
    ) = scService.getAllScContractsPaginated(page, count, creator, admin, label)

    @ApiOperation("Returns detail about a smart contract")
    @GetMapping("/contract/{contract}")
    fun getContract(@ApiParam(value = "The contract address") @PathVariable contract: String) =
        scService.getContract(contract)

    @ApiOperation("Returns a smart contract's history")
    @GetMapping("/contract/{contract}/history")
    fun getContractHistory(@ApiParam(value = "The contract address") @PathVariable contract: String) =
        scService.getHistoryByContract(contract)

    @ApiOperation("Returns a distinct list of known contract labels")
    @GetMapping("/contract/labels")
    fun getContractLabels() = scService.getContractLabels()
}
