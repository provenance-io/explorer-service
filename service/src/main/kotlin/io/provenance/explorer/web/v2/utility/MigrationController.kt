package io.provenance.explorer.web.v2.utility

import io.provenance.explorer.config.interceptor.JwtInterceptor
import io.provenance.explorer.domain.annotation.HiddenApi
import io.provenance.explorer.domain.extensions.TxMessageBody
import io.provenance.explorer.domain.extensions.toTxBody
import io.provenance.explorer.domain.extensions.toTxMessageBody
import io.provenance.explorer.domain.models.explorer.GovSubmitProposalRequest
import io.provenance.explorer.domain.models.explorer.ProposalType
import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.utility.MigrationService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@Validated
@RestController
@RequestMapping(path = ["/api/v2/migration"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    value = "Migration controller", produces = "application/json", consumes = "application/json", tags = ["Migrations"],
    description = "This should NEVER be used by the UI"
)
@HiddenApi
class MigrationController(private val migrationService: MigrationService, private val assetService: AssetService) {

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

    @ApiOperation("Updates blocks from list, specifying whether to reprocess from DB or chain")
    @PutMapping("/update/blocks/list")
    fun updateBlocksList(@RequestParam pullFromDb: Boolean, @RequestBody blocks: List<Int>) =
        ResponseEntity.ok(migrationService.insertBlocks(blocks, pullFromDb))

    @ApiOperation("Updates denom units")
    @GetMapping("/update/denom/units")
    fun updateDenomUnits() = ResponseEntity.ok(assetService.updateMarkerUnit())


    @ApiOperation(value = "inserts the dlob data")
    @PostMapping("/insert/dlob")
    fun insertDlobRecords() = ResponseEntity.ok(migrationService.getFromDlob())
}
