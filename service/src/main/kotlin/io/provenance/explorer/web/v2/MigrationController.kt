package io.provenance.explorer.web.v2

import io.provenance.explorer.service.MigrationService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v2"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    value = "Migration controller", produces = "application/json", consumes = "application/json", tags = ["Migrations"],
    description = "This should NEVER be used by the UI"
)
class MigrationController(private val migrationService: MigrationService) {

    @ApiOperation("Updates existing transactions with given type ids with new data points")
    @GetMapping("/update/txs/type")
    fun updateTxsByType(
        @RequestParam typeIds: List<Int>,
        @RequestParam min: Int,
        @RequestParam max: Int,
        @RequestParam inc: Int
    ) = ResponseEntity.ok(migrationService.reprocessTxsFromTypeId(typeIds, min, max, inc))

    @ApiOperation("Updates existing transactions with given heights with new data points")
    @PutMapping("/update/txs/height")
    fun updateTxsByHeight(@RequestBody heights: List<Int>) =
        ResponseEntity.ok(migrationService.reprocessTxsFromHeight(heights))

    @ApiOperation("Updates validator cache for missing records")
    @GetMapping("/update/validatorCache")
    fun updateValidatorCache() = ResponseEntity.ok(migrationService.updateValidatorsCache())

    @ApiOperation("Updates existing blocks with proposer records")
    @GetMapping("/update/block/proposers")
    fun updateBlockProposers(@RequestParam min: Int, @RequestParam max: Int, @RequestParam limit: Int) =
        ResponseEntity.ok(migrationService.updateProposers(min, max, limit))

    @ApiOperation("Updates accounts with data")
    @PutMapping("/update/accounts")
    fun updateAccounts(@RequestBody accounts: List<String>) =
        ResponseEntity.ok(migrationService.updateAccounts(accounts))

    @ApiOperation("Updates missed blocks")
    @GetMapping("/update/blocks/missed")
    fun updateMissedBlocks(@RequestParam start: Int, @RequestParam end: Int, @RequestParam inc: Int) =
        ResponseEntity.ok(migrationService.updateMissedBlocks(start, end, inc))
}
