package io.provenance.explorer.web.v2

import io.provenance.explorer.service.MigrationService
import io.provenance.explorer.web.BaseController
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v2"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    value = "Migration controller", produces = "application/json", consumes = "application/json", tags = ["Migrations"],
    description = "This should NEVER be used by the UI"
)
class MigrationController(private val migrationService: MigrationService) : BaseController() {

    @ApiOperation("Updates existing transactions with new data points")
    @GetMapping("/update/txs")
    fun updateTxs(): ResponseEntity<Boolean> = ResponseEntity.ok(migrationService.updateTxs())

    @ApiOperation("Updates validator cache for missing records")
    @GetMapping("/update/validatorCache")
    fun updateValidatorCache() = ResponseEntity.ok(migrationService.updateValidatorsCache())

    @ApiOperation("Updates existing blocks with proposer records")
    @GetMapping("/update/block/proposers")
    fun updateBlockProposers(): ResponseEntity<Boolean> = ResponseEntity.ok(migrationService.updateProposers())

}
