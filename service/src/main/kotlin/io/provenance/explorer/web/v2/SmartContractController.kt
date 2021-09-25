package io.provenance.explorer.web.v2

import io.provenance.explorer.service.SmartContractService
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
@RequestMapping(path = ["/api/v2/smart_contract"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    value = "Smart Contract controller", produces = "application/json", consumes = "application/json",
    tags =
    ["Smart Contract"]
)
class SmartContractController(private val scService: SmartContractService) {

    @ApiOperation("Returns a smart contract code object")
    @GetMapping("/code/{id}")
    fun getCode(@PathVariable id: Int) = ResponseEntity.ok(scService.getCode(id))

    @ApiOperation("Returns paginated list of smart contracts by code id, creation block Height DESC")
    @GetMapping("/code/{id}/contracts")
    fun getContractsByCode(
        @PathVariable id: Int,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int
    ) = ResponseEntity.ok(scService.getContractsByCode(id, page, count))

    @ApiOperation("Returns paginated list of smart contract codes, code ID descending")
    @GetMapping("/contract/all")
    fun getContractsList(
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int
    ) = ResponseEntity.ok(scService.getAllScContractsPaginated(page, count))

    @ApiOperation("Returns a smart contract object")
    @GetMapping("/contract/{contract}")
    fun getContract(@PathVariable contract: String) = ResponseEntity.ok(scService.getContract(contract))

    @ApiOperation("Returns a smart contract's history")
    @GetMapping("/contract/{contract}/history")
    fun getContractHistory(@PathVariable contract: String) = ResponseEntity.ok(scService.getHistoryByContract(contract))
}
