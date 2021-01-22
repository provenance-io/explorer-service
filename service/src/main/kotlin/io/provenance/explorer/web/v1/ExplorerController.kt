package io.provenance.explorer.web.v1

import io.provenance.explorer.config.ServiceProperties
import io.provenance.explorer.domain.models.explorer.ValidatorDetails
import io.provenance.explorer.service.ExplorerService
import io.provenance.explorer.web.BaseController
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.joda.time.DateTime
import org.springframework.format.annotation.DateTimeFormat
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
@RequestMapping(value = ["/api/v1"])
@Api(value = "Explorer controller",
    produces = "application/json",
    consumes = "application/json",
    tags = ["General V1"],
    hidden = true)
class ExplorerController(private val serviceProperties: ServiceProperties,
                         private val explorerService: ExplorerService) : BaseController() {

    @ApiOperation(value = "Return the latest block transactions")
    @GetMapping(value = ["/recent/txs"],
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun txsRecent(@RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
                  @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
                  @RequestParam(required = false, defaultValue = "desc") sort: String = "desc"):
        ResponseEntity<Any> = ResponseEntity.ok(explorerService.getRecentTransactions(count, page, sort))

    @ApiOperation(value = "Return transaction by hash value")
    @GetMapping(value = ["/tx"],
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun txByHash(@RequestParam(required = true) hash: String):
        ResponseEntity<Any> = ResponseEntity.ok(explorerService.getTransactionByHash(hash))

    @ApiOperation(value = "Return transaction by block height")
    @GetMapping(value = ["/txs"],
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun txByBlockHeight(@RequestParam(required = true) height: Int):
        ResponseEntity<Any> = ResponseEntity.ok(explorerService.getTransactionsByHeight(height))

    @ApiOperation(value = "Get X-Day Transaction History")
    @GetMapping(value = ["/txs/history"],
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun txHistory(@RequestParam(required = true)
                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime,
                  @RequestParam(required = true)
                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime,
                  @RequestParam(required = false, defaultValue = "day") granularity: String
    ): ResponseEntity<Any> =
        ResponseEntity.ok(explorerService.getTransactionHistory(fromDate, toDate, granularity))

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
    fun validatorsV2(@RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
                     @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
                     @RequestParam(required = false, defaultValue = "desc") sort: String,
                     @RequestParam(required = false, defaultValue = "BOND_STATUS_BONDED") status: String):
        ResponseEntity<Any> = ResponseEntity.ok(explorerService.getRecentValidators(count, page, sort, status))

    @ApiOperation(value = "Returns validator by address id")
    @GetMapping(value = ["/validator"],
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun validator(@RequestParam(required = true) id: String): ResponseEntity<ValidatorDetails?> =
        ResponseEntity.ok(explorerService.getValidator(id))

    @ApiOperation(value = "Returns set of validators at block height")
    @GetMapping(value = ["/validators"],
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun validatorsAtHeight(@RequestParam(required = true) blockHeight: Int,
                           @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
                           @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
                           @RequestParam(required = false, defaultValue = "desc") sort: String):
        ResponseEntity<Any> =
        ResponseEntity.ok(explorerService.getValidatorsAtHeight(blockHeight, count, page, sort, "BOND_STATUS_BONDED"))

    @ApiOperation(value = "Returns sportlight statistics")
    @GetMapping(value = ["/spotlight"],
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun spotlight(): ResponseEntity<Any> = ResponseEntity.ok(explorerService.getSpotlightStatistics())

    @ApiOperation(value = "Returns sportlight statistics")
    @GetMapping(value = ["/gasStats"],
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun gasStatistics(@RequestParam(required = true)
                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime,
                      @RequestParam(required = true)
                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime,
                      @RequestParam(required = false, defaultValue = "day") granularity: String
    ): ResponseEntity<Any> = ResponseEntity.ok(explorerService.getGasStatistics(fromDate, toDate, granularity))

    @ApiOperation(value = "Returns transaction json")
    @GetMapping(value = ["/tx/{txnHash}/json"],
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun transactionJson(@PathVariable txnHash: String): ResponseEntity<Any> =
        ResponseEntity.ok(explorerService.getTransactionJson(txnHash))

    @ApiOperation(value = "Returns the ID of the chain associated with the explorer instance")
    @GetMapping(value = ["/chain/id"],
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getChainId(): ResponseEntity<String> = ResponseEntity.ok(explorerService.getChainId())
}
