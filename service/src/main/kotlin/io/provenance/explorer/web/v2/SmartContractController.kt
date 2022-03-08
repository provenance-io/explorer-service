package io.provenance.explorer.web.v2

import io.provenance.explorer.service.SmartContractService
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
@RequestMapping(path = ["/api/v2/smart_contract"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Smart Contract-related endpoints",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Smart Contract"]
)
class SmartContractController(private val scService: SmartContractService) {

    @ApiOperation("Returns a paginated list of smart contract codes, code ID descending")
    @GetMapping("/codes/all")
    fun getCodesList(
        @ApiParam(defaultValue = "1", required = false) @RequestParam(defaultValue = "1") @Min(1) page: Int,
        @ApiParam(value = "Record count between 1 and 50", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) count: Int
    ) = ResponseEntity.ok(scService.getAllScCodesPaginated(page, count))

    @ApiOperation("Returns a smart contract code object")
    @GetMapping("/code/{id}")
    fun getCode(
        @ApiParam(value = "The ID of the original code entry") @PathVariable id: Int
    ) = ResponseEntity.ok(scService.getCode(id))

    @ApiOperation("Returns a paginated list of smart contracts by code ID, creation block height descending")
    @GetMapping("/code/{id}/contracts")
    fun getContractsByCode(
        @ApiParam(value = "The ID of the original code entry") @PathVariable id: Int,
        @ApiParam(defaultValue = "1", required = false) @RequestParam(defaultValue = "1") @Min(1) page: Int,
        @ApiParam(value = "Record count between 1 and 100", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) count: Int
    ) = ResponseEntity.ok(scService.getContractsByCode(id, page, count))

    @ApiOperation("Returns paginated list of smart contracts, creation block height descending")
    @GetMapping("/contract/all")
    fun getContractsList(
        @ApiParam(defaultValue = "1", required = false) @RequestParam(defaultValue = "1") @Min(1) page: Int,
        @ApiParam(value = "Record count between 1 and 100", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) count: Int
    ) = ResponseEntity.ok(scService.getAllScContractsPaginated(page, count))

    @ApiOperation("Returns detail about a smart contract ")
    @GetMapping("/contract/{contract}")
    fun getContract(@PathVariable contract: String) = ResponseEntity.ok(scService.getContract(contract))

    @ApiOperation("Returns a smart contract's history")
    @GetMapping("/contract/{contract}/history")
    fun getContractHistory(@PathVariable contract: String) = ResponseEntity.ok(scService.getHistoryByContract(contract))
}
