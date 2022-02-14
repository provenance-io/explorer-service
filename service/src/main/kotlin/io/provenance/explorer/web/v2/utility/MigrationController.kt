package io.provenance.explorer.web.v2.utility

import io.provenance.explorer.domain.annotation.HiddenApi
import io.provenance.explorer.service.utility.MigrationService
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
@RequestMapping(path = ["/api/v2/migration"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    value = "Migration controller", produces = "application/json", consumes = "application/json", tags = ["Migrations"],
    description = "This should NEVER be used by the UI"
)
@HiddenApi
class MigrationController(private val migrationService: MigrationService) {

    @ApiOperation("Updates accounts with data")
    @PutMapping("/update/accounts")
    fun updateAccounts(@RequestBody accounts: List<String>) =
        ResponseEntity.ok(migrationService.updateAccounts(accounts))

    @ApiOperation("Updates missed blocks")
    @GetMapping("/update/blocks/missed")
    fun updateMissedBlocks(@RequestParam start: Int, @RequestParam end: Int, @RequestParam inc: Int) =
        ResponseEntity.ok(migrationService.updateMissedBlocks(start, end, inc))

    @ApiOperation("Updates blocks")
    @GetMapping("/update/blocks")
    fun updateBlocks(@RequestParam start: Int, @RequestParam end: Int, @RequestParam inc: Int) =
        ResponseEntity.ok(migrationService.updateBlocks(start, end, inc))
}
