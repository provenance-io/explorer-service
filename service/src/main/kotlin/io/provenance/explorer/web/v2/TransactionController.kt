package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.models.explorer.DateTruncGranularity
import io.provenance.explorer.domain.models.explorer.MsgTypeSet
import io.provenance.explorer.domain.models.explorer.TxStatus
import io.provenance.explorer.service.TransactionService
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
@Api(
    value = "Transaction controller",
    produces = "application/json",
    consumes = "application/json",
    tags = ["Transactions"]
)
class TransactionController(private val transactionService: TransactionService) {

    @ApiOperation("Return the latest transactions with query params")
    @GetMapping("/recent")
    fun txsRecent(
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
        @RequestParam(required = false) msgType: String?,
        @RequestParam(required = false) txStatus: TxStatus?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime?
    ) =
        ResponseEntity.ok(
            transactionService.getTxsByQuery(
                null, null, null, msgType, null, txStatus, count, page, fromDate,
                toDate, null
            )
        )

    @ApiOperation("Return transaction by hash value")
    @GetMapping("/{hash}")
    fun txByHash(@PathVariable hash: String) = ResponseEntity.ok(transactionService.getTransactionByHash(hash))

    @ApiOperation("Return transaction msgs by hash value")
    @GetMapping("/{hash}/msgs")
    fun txMsgsByHash(
        @PathVariable hash: String,
        @RequestParam(required = false) msgType: String?,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int
    ) = ResponseEntity.ok(transactionService.getTxMsgsPaginated(hash, msgType, page, count))

    @ApiOperation("Returns transaction json by hash value")
    @GetMapping("/{hash}/json")
    fun transactionJson(@PathVariable hash: String) = ResponseEntity.ok(transactionService.getTransactionJson(hash))

    @ApiOperation("Return transaction by block height")
    @GetMapping("/height/{height}")
    fun txByBlockHeight(
        @PathVariable height: Int,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int
    ) = ResponseEntity.ok(
        transactionService.getTxsByQuery(
            null, null, null, null, height, null, count, page, null, null, null
        )
    )

    @ApiOperation("Get Transactions Heatmap")
    @GetMapping("/heatmap")
    fun txHeatmap() = ResponseEntity.ok(transactionService.getTxHeatmap())

    @ApiOperation("Get X-Day Transaction History")
    @GetMapping("/history")
    fun txHistory(
        @RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime,
        @RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime,
        @RequestParam(required = false) granularity: DateTruncGranularity?
    ) = ResponseEntity.ok(transactionService.getTxHistoryByQuery(fromDate, toDate, granularity))

    @ApiOperation("Return list of transaction types")
    @GetMapping("/types")
    fun txTypes() = ResponseEntity.ok(transactionService.getTxTypes(null))

    @ApiOperation("Return list of transaction types by Module")
    @GetMapping("/types/{module}")
    fun txTypesByModule(@PathVariable module: MsgTypeSet) =
        ResponseEntity.ok(transactionService.getTxTypes(module))

    @ApiOperation("Return list of transaction types by tx hash")
    @GetMapping("/types/tx/{hash}")
    fun txTypesByTxHash(@PathVariable hash: String) =
        ResponseEntity.ok(transactionService.getTxTypesByTxHash(hash))

    @ApiOperation("Returns transactions by query params for a specific module of msg types")
    @GetMapping("/module/{module}")
    fun txsByModule(
        @PathVariable module: MsgTypeSet,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
        @RequestParam(required = false) msgType: String?,
        @RequestParam(required = false) txStatus: TxStatus?,
        @RequestParam(required = false) address: String?,
        @RequestParam(required = false) denom: String?,
        @RequestParam(required = false) nftAddr: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime?
    ) =
        ResponseEntity.ok(
            transactionService.getTxsByQuery(
                address, denom, module, msgType, null, txStatus, count, page, fromDate, toDate, nftAddr
            )
        )

    @ApiOperation("Returns transactions by query params for a specific address")
    @GetMapping("/address/{address}")
    fun txsByAddress(
        @PathVariable address: String,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
        @RequestParam(required = false) msgType: String?,
        @RequestParam(required = false) txStatus: TxStatus?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime?
    ) =
        ResponseEntity.ok(
            transactionService.getTxsByQuery(
                address, null, null, msgType, null, txStatus, count, page, fromDate, toDate, null
            )
        )

    @ApiOperation("Returns transactions by query params for a specific nft address")
    @GetMapping("/nft/{nftAddr}")
    fun txsByNftAddress(
        @PathVariable nftAddr: String,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
        @RequestParam(required = false) msgType: String?,
        @RequestParam(required = false) txStatus: TxStatus?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime?
    ) =
        ResponseEntity.ok(
            transactionService.getTxsByQuery(
                null, null, null, msgType, null, txStatus, count, page, fromDate, toDate, nftAddr
            )
        )

    @ApiOperation("Returns transactions for governance module with unique response type")
    @GetMapping("/module/gov")
    fun txsForGovernance(
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
        @RequestParam(required = false) address: String?,
        @RequestParam(required = false) msgType: String?,
        @RequestParam(required = false) txStatus: TxStatus?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: DateTime?
    ) =
        ResponseEntity.ok(transactionService.getGovernanceTxs(address, msgType, txStatus, page, count, fromDate, toDate))
}
