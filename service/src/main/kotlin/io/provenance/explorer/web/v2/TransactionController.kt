package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.PagedResults
import io.provenance.explorer.domain.PbTransaction
import io.provenance.explorer.domain.RecentTx
import io.provenance.explorer.domain.TxDetails
import io.provenance.explorer.domain.TxHistory
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
@RequestMapping(path = ["/api/v2/txs"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(value = "Transaction controller", produces = "application/json", consumes = "application/json", tags = ["Transactions"])
class TransactionController(private val explorerService: ExplorerService) : BaseController() {

    @ApiOperation("Return the latest block transactions")
    @GetMapping("/recent")
    fun txsRecent(@RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
                  @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
                  @RequestParam(required = false, defaultValue = "desc") sort: String = "desc"):
        ResponseEntity<PagedResults<RecentTx>> = ResponseEntity.ok(explorerService.getRecentTransactions(count, page, sort))

    @ApiOperation("Return transaction by hash value")
    @GetMapping("/{hash}")
    fun txByHash(@PathVariable hash: String):
        ResponseEntity<TxDetails> = ResponseEntity.ok(explorerService.getTransactionByHash(hash))

    @ApiOperation("Returns transaction json by hash value")
    @GetMapping("/{hash}/json")
    fun transactionJson(@PathVariable hash: String):
        ResponseEntity<PbTransaction> = ResponseEntity.ok(explorerService.getTransactionJson(hash))

    @ApiOperation("Return transaction by block height")
    @GetMapping("/height/{height}")
    fun txByBlockHeight(@PathVariable height: Int):
        ResponseEntity<List<TxDetails>> = ResponseEntity.ok(explorerService.getTransactionsByHeight(height))

    @ApiOperation("Get X-Day Transaction History")
    @GetMapping("/history")
    fun txHistory(@RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime,
                  @RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime,
                  @RequestParam(required = false, defaultValue = "day") granularity: String
    ): ResponseEntity<MutableList<TxHistory>> =
        ResponseEntity.ok(explorerService.getTransactionHistory(fromDate, toDate, granularity))

}
