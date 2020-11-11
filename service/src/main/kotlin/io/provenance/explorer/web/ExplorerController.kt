package io.provenance.explorer.web

import io.provenance.explorer.config.ServiceProperties
import io.provenance.explorer.service.TendermintService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(value = ["/api/v1"])
@Api(value = "explorer", description = "Provenance Explorer")
class ExplorerController(private val serviceProperties: ServiceProperties,
                         private val tendermintService: TendermintService) : BaseController() {

    @ApiOperation(value = "Echo echo echo echo echo....")
    @PostMapping(value = ["/echo"],
            consumes = [MediaType.APPLICATION_JSON_VALUE],
            produces = [MediaType.APPLICATION_JSON_VALUE])
    fun echo(@RequestBody echo: Any): ResponseEntity<Any> = if (user() != null) {
        throw AccessDeniedException("access denied")
    } else {
        ResponseEntity.ok(echo)
    }

    @ApiOperation(value = "Return the latest block transactions")
    @GetMapping(value = ["/recent/txs"],
            produces = [MediaType.APPLICATION_JSON_VALUE])
    fun txsRecent(@RequestParam(required = false, defaultValue = "10") count: Int, @RequestParam(required = false, defaultValue = "0") page: Int, @RequestParam(required = false, defaultValue = "desc") sort: String = "desc"):
            ResponseEntity<Any> = ResponseEntity.ok(tendermintService.getRecentTransactions(count, page, sort))

    @ApiOperation(value = "Return block at specified height")
    @GetMapping(value = ["/block"],
            produces = [MediaType.APPLICATION_JSON_VALUE])
    fun blockHeight(@RequestParam(required = false) height: Long?): ResponseEntity<Any> = ResponseEntity.ok(tendermintService.getBlockAtHeight(height))

    @ApiOperation(value = "Return transaction by hash value")
    @GetMapping(value = ["/tx"],
            produces = [MediaType.APPLICATION_JSON_VALUE])
    fun txByHash(@RequestParam(required = true) hash: String): ResponseEntity<Any> = ResponseEntity.ok("{}")

    @ApiOperation(value = "/recent/blocks?count&page&sort - Recent Blocks")
    @GetMapping(value = ["/recent/blocks"],
            produces = [MediaType.APPLICATION_JSON_VALUE])
    fun recentBlocks(@RequestParam(required = true, defaultValue = "10") count: Int, @RequestParam(required = true, defaultValue = "0") page: Int, @RequestParam(required = false, defaultValue = "desc") sort: String):
            ResponseEntity<Any> = ResponseEntity.ok(tendermintService.getRecentBlocks(count, page, sort))

    @ApiOperation(value = "/validators/?count&page&sort - Get Validators (ex: Top 10 Validators)")
    @GetMapping(value = ["/validators/"],
            produces = [MediaType.APPLICATION_JSON_VALUE])
    fun validators(@RequestParam(required = true) count: Int, @RequestParam(required = false) sort: String): ResponseEntity<Any> = ResponseEntity.ok("{}")

    @ApiOperation(value = "/txs/history?from&to - Get X-Day Transaction History")
    @GetMapping(value = ["/txs/history"],
            produces = [MediaType.APPLICATION_JSON_VALUE])
    fun txHistory(@RequestParam(required = true) from: String, @RequestParam(required = true) to: Int): ResponseEntity<Any> = ResponseEntity.ok("{}")
}