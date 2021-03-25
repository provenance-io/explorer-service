package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.entities.UnknownTxType
import io.provenance.explorer.service.MigrationService
import io.provenance.explorer.web.BaseController
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v2"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(value = "Migration controller", produces = "application/json", consumes = "application/json", tags = ["Migrations"])
class MigrationController(private val migrationService: MigrationService) : BaseController() {

    @ApiOperation("Populates existing signatures into the new signature tables.")
    @GetMapping("/populate/sigs")
    fun populateSigs(): ResponseEntity<Boolean> = ResponseEntity.ok(migrationService.populateSigs())

    @ApiOperation("Updates existing transactions with new data points")
    @GetMapping("/update/txs")
    fun updateTxs(): ResponseEntity<Boolean> = ResponseEntity.ok(migrationService.updateTxs())

    @ApiOperation("Updates existing transaction msg type with the given info")
    @PostMapping("/update/txMsgType")
    fun updateTxMessageType(@RequestBody txMsgType: UnknownTxType) =
        ResponseEntity.ok(migrationService.updateTxMsgType(txMsgType))

    @ApiOperation("Fetches common error types in the data")
    @GetMapping("/errors")
    fun getErrors() = ResponseEntity.ok(migrationService.getErrors())

    @ApiOperation("For Testing")
    @GetMapping("/test/json")
    fun jsonTest(@RequestParam txHash: String) = ResponseEntity.ok(migrationService.translateMsgAny(txHash))
}
