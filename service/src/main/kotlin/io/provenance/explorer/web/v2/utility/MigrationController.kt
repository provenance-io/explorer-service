package io.provenance.explorer.web.v2.utility

import io.provenance.explorer.domain.annotation.HiddenApi
import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.utility.MigrationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
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
@RequestMapping(path = ["/api/v2/migration"], produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [org.springframework.http.MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "Migrations",
    description = "This should NEVER be used by the UI"
)
@HiddenApi
class MigrationController(private val migrationService: MigrationService, private val assetService: AssetService) {

    @Operation(summary = "Updates accounts with data")
    @PutMapping("/update/accounts")
    fun updateAccounts(@RequestBody accounts: List<String>) =
        ResponseEntity.ok(migrationService.updateAccounts(accounts))

    @Operation(summary = "Updates missed blocks")
    @GetMapping("/update/blocks/missed")
    fun updateMissedBlocks(@RequestParam start: Int, @RequestParam end: Int, @RequestParam inc: Int) =
        ResponseEntity.ok(migrationService.updateMissedBlocks(start, end, inc))

    @Operation(summary = "Updates blocks")
    @GetMapping("/update/blocks")
    fun updateBlocks(@RequestParam start: Int, @RequestParam end: Int, @RequestParam inc: Int) =
        ResponseEntity.ok(migrationService.updateBlocks(start, end, inc))

    @Operation(summary = "Updates blocks from list, specifying whether to reprocess from DB or chain")
    @PutMapping("/update/blocks/list")
    fun updateBlocksList(@RequestParam pullFromDb: Boolean, @RequestBody blocks: List<Int>) =
        ResponseEntity.ok(migrationService.insertBlocks(blocks, pullFromDb))

    @Operation(summary = "Updates denom units")
    @GetMapping("/update/denom/units")
    fun updateDenomUnits() = ResponseEntity.ok(assetService.updateMarkerUnit())
}
