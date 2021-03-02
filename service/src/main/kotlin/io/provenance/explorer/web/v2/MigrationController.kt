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
@Api(value = "Migration controller", produces = "application/json", consumes = "application/json", tags = ["Migrations"])
class MigrationController(private val migrationService: MigrationService) : BaseController() {

    @ApiOperation("Populates existing signatures into the new signature tables.")
    @GetMapping("/populate/sigs")
    fun populateSigs(): ResponseEntity<Boolean> = ResponseEntity.ok(migrationService.populateSigs())

    @ApiOperation("Populates existing transactions so people dont have to wait for 500K blocks to process.")
    @GetMapping("/populate/txs")
    fun populateTxs(): ResponseEntity<Boolean> = ResponseEntity.ok(migrationService.populateTxs())
}
