package io.provenance.explorer.web

import io.provenance.explorer.config.ServiceProperties
import io.provenance.explorer.service.ExplorerService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.text.SimpleDateFormat
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(value = ["/api/v1"])
@Api(value = "explorer", description = "Provenance Explorer")
class ExplorerController(private val serviceProperties: ServiceProperties,
                         private val explorerService: ExplorerService) : BaseController() {

    @ApiOperation(value = "Return the latest block transactions")
    @GetMapping(value = ["/recent/txs"],
            produces = [MediaType.APPLICATION_JSON_VALUE])
    fun txsRecent(@RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
                  @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
                  @RequestParam(required = false, defaultValue = "desc") sort: String = "desc"):
            ResponseEntity<Any> = ResponseEntity.ok(explorerService.getRecentTransactions(count, page - 1, sort))

    @ApiOperation(value = "Return transaction by hash value")
    @GetMapping(value = ["/tx"],
            produces = [MediaType.APPLICATION_JSON_VALUE])
    fun txByHash(@RequestParam(required = true) hash: String):
            ResponseEntity<Any> = ResponseEntity.ok(explorerService.getTransactionByHash(hash))

    @ApiOperation(value = "Get X-Day Transaction History")
    @GetMapping(value = ["/txs/history"],
            produces = [MediaType.APPLICATION_JSON_VALUE])
    fun txHistory(@RequestParam(required = true) fromDate: String,
                  @RequestParam(required = true) toDate: String):
            ResponseEntity<Any> = ResponseEntity.ok(explorerService.getTransactionHistory(fromDate, toDate))

    @ApiOperation(value = "Return block at specified height")
    @GetMapping(value = ["/block"],
            produces = [MediaType.APPLICATION_JSON_VALUE])
    fun blockHeight(@RequestParam(required = false) height: Int?):
            ResponseEntity<Any> = ResponseEntity.ok(explorerService.getBlockAtHeight(height))

    @ApiOperation(value = "Returns most recent blocks")
    @GetMapping(value = ["/recent/blocks"],
            produces = [MediaType.APPLICATION_JSON_VALUE])
    fun recentBlocks(@RequestParam(required = true, defaultValue = "10") @Min(1) count: Int,
                     @RequestParam(required = true, defaultValue = "1") @Min(1) page: Int,
                     @RequestParam(required = false, defaultValue = "desc") sort: String):
            ResponseEntity<Any> = ResponseEntity.ok(explorerService.getRecentBlocks(count, page - 1, sort))

    @ApiOperation(value = "Returns recent validators")
    @GetMapping(value = ["/recent/validators"],
            produces = [MediaType.APPLICATION_JSON_VALUE])
    fun validators(@RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
                   @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
                   @RequestParam(required = false, defaultValue = "desc") sort: String):
            ResponseEntity<Any> = ResponseEntity.ok(explorerService.getRecentValidators(count, page - 1, sort))

    @ApiOperation(value = "Returns validator by address id")
    @GetMapping(value = ["/validator"],
            produces = [MediaType.APPLICATION_JSON_VALUE])
    fun validator(@RequestParam(required = true) id: String):
            ResponseEntity<Any> = ResponseEntity.ok(explorerService.getValidator(id))

    @ApiOperation(value = "Returns set of validators at block height")
    @GetMapping(value = ["/validators"],
            produces = [MediaType.APPLICATION_JSON_VALUE])
    fun validatorsAtHeight(@RequestParam(required = true) blockHeight: Int,
                           @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
                           @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
                           @RequestParam(required = false, defaultValue = "desc") sort: String):
            ResponseEntity<Any> = ResponseEntity.ok(explorerService.getValidatorsAtHeight(blockHeight, count, page - 1, sort))

}