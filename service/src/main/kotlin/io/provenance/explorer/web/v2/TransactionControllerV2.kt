package io.provenance.explorer.web.v2

import io.provenance.explorer.model.MsgTypeSet
import io.provenance.explorer.model.TxStatus
import io.provenance.explorer.service.TransactionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.joda.time.DateTime
import org.springframework.cache.annotation.Cacheable
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v2/txs"], produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [org.springframework.http.MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "Transactions",
    description = "Transaction endpoints"
)
class TransactionControllerV2(private val transactionService: TransactionService) {

    @Operation(summary = "Return the latest transactions with query params")
    @GetMapping("/recent")
    @Cacheable(value = ["responses"], key = "{#root.methodName, #count, #page, #msgType, #txStatus, #fromDate, #toDate}")
    fun txsRecent(
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 200", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @Parameter(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @Parameter(required = false)
        @RequestParam(required = false)
        txStatus: TxStatus?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime?
    ) = transactionService.getTxsByQuery(
        msgType = msgType,
        txStatus = txStatus,
        count = count,
        page = page,
        fromDate = fromDate,
        toDate = toDate
    )

    @Operation(summary = "Return transaction detail by hash value")
    @GetMapping("/{hash}")
    fun txByHash(
        @PathVariable hash: String,
        @Parameter(required = false)
        @RequestParam(required = false)
        blockHeight: Int? = null
    ) = transactionService.getTransactionByHash(hash, blockHeight)

    @Operation(summary = "Return a transaction's messages by tx hash value")
    @GetMapping("/{hash}/msgs")
    fun txMsgsByHash(
        @PathVariable hash: String,
        @Parameter(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 200", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @Parameter(required = false)
        @RequestParam(required = false)
        blockHeight: Int? = null
    ) = transactionService.getTxMsgsPaginated(hash, msgType, page, count, blockHeight)

    @Operation(summary = "Returns a transaction object as JSON by tx hash value")
    @GetMapping("/{hash}/json")
    fun transactionJson(
        @PathVariable hash: String,
        @Parameter(required = false)
        @RequestParam(required = false)
        blockHeight: Int? = null
    ) = transactionService.getTransactionJson(hash, blockHeight)

    @Operation(summary = "Returns transactions by block height")
    @GetMapping("/height/{height}")
    fun txByBlockHeight(
        @PathVariable height: Int,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 200", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int
    ) = transactionService.getTxsByQuery(txHeight = height, count = count, page = page)

    @Operation(summary = "Returns a heatmap of transaction activity on chain")
    @GetMapping("/heatmap")
    @Deprecated("Use /api/v3/txs/heatmap")
    @java.lang.Deprecated
    fun txHeatmap() = transactionService.getTxHeatmap()

    @Operation(summary = "Return list of transaction types")
    @GetMapping("/types")
    fun txTypes() = transactionService.getTxTypes(null)

    @Operation(summary = "Return list of transaction types by Module")
    @GetMapping("/types/{module}")
    fun txTypesByModule(@PathVariable module: MsgTypeSet) = transactionService.getTxTypes(module)

    @Operation(summary = "Return list of transaction types by tx hash")
    @GetMapping("/types/tx/{hash}")
    @Deprecated("Use /api/v3/txs/{hash}/types")
    @java.lang.Deprecated
    fun txTypesByTxHash(
        @PathVariable hash: String,
        @Parameter(required = false)
        @RequestParam(required = false)
        blockHeight: Int? = null
    ) = transactionService.getTxTypesByTxHash(hash)

    @Operation(summary = "Returns transactions by query params for a specific module of msg types")
    @GetMapping("/module/{module}")
    fun txsByModule(
        @PathVariable module: MsgTypeSet,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 200", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @Parameter(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @Parameter(required = false)
        @RequestParam(required = false)
        txStatus: TxStatus?,
        @Parameter(description = "Use either the standard address or a validator operator address", required = false)
        @RequestParam(required = false)
        address: String?,
        @Parameter(description = "Use any of the denom units to search, ie `nhash` or `hash`", required = false)
        @RequestParam(required = false)
        denom: String?,
        @Parameter(description = "Use the nft address type, ie. scope, scopespec, or the scope UUID", required = false)
        @RequestParam(required = false)
        nftAddr: String?,
        @Parameter(description = "Use the chain identifying name, ie. osmosis-1", required = false)
        @RequestParam(required = false)
        ibcChain: String?,
        @Parameter(description = "Use the port portion of a source IBC channel, ie. transfer", required = false)
        @RequestParam(required = false)
        ibcSrcPort: String?,
        @Parameter(description = "Use the channel portion of a source IBC channel, ie. channel-5", required = false)
        @RequestParam(required = false)
        ibcSrcChannel: String?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime?
    ) = transactionService.getTxsByQuery(
        address, denom, module, msgType, null, txStatus, count, page, fromDate, toDate, nftAddr,
        ibcChain, ibcSrcPort, ibcSrcChannel
    )

    @Operation(summary = "Returns transactions by query params for a specific address")
    @GetMapping("/address/{address}")
    fun txsByAddress(
        @Parameter(description = "Use either the standard address or a validator operator address")
        @PathVariable
        address: String,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 200", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @Parameter(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @Parameter(required = false)
        @RequestParam(required = false)
        txStatus: TxStatus?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime?
    ) = transactionService.getTxsByQuery(
        address = address,
        msgType = msgType,
        txStatus = txStatus,
        count = count,
        page = page,
        fromDate = fromDate,
        toDate = toDate
    )

    @Operation(summary = "Returns transactions by query params for a specific nft address")
    @GetMapping("/nft/{nftAddr}")
    fun txsByNftAddress(
        @Parameter(description = "Use the nft address type, ie. scope, scopespec, or the scope UUID")
        @PathVariable
        nftAddr: String,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 200", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @Parameter(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @Parameter(required = false)
        @RequestParam(required = false)
        txStatus: TxStatus?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime?
    ) = transactionService.getTxsByQuery(
        msgType = msgType,
        txStatus = txStatus,
        count = count,
        page = page,
        fromDate = fromDate,
        toDate = toDate,
        nftAddr = nftAddr
    )

    @Operation(summary = "Returns transactions for governance module with unique response type")
    @GetMapping("/module/gov")
    fun txsForGovernance(
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 200", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @Parameter(description = "Use either the standard address or a validator operator address", required = false)
        @RequestParam(required = false)
        address: String?,
        @Parameter(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @Parameter(required = false)
        @RequestParam(required = false)
        txStatus: TxStatus?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime?
    ) = transactionService.getGovernanceTxs(address, msgType, txStatus, page, count, fromDate, toDate)

    @Operation(summary = "Returns transactions for smart contract module with unique response type")
    @GetMapping("/module/smart_contract")
    fun txsForSmartContracts(
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 200", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @Parameter(description = "The base code ID", required = false)
        @RequestParam(required = false)
        code: Int?,
        @Parameter(description = "The contract address", required = false)
        @RequestParam(required = false)
        contract: String?,
        @Parameter(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @Parameter(required = false)
        @RequestParam(required = false)
        txStatus: TxStatus?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime?
    ) = transactionService.getSmartContractTxs(code, contract, msgType, txStatus, page, count, fromDate, toDate)

    @Operation(summary = "Returns transactions for the IBC module with standard response type")
    @GetMapping("/ibc/chain/{ibcChain}")
    fun txsForIbc(
        @Parameter(description = "Use the chain identifying name, ie. osmosis-1")
        @PathVariable
        ibcChain: String,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 200", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @Parameter(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @Parameter(required = false)
        @RequestParam(required = false)
        txStatus: TxStatus?,
        @Parameter(description = "Use the port portion of a source IBC channel, ie. transfer", required = false)
        @RequestParam(required = false)
        ibcSrcPort: String?,
        @Parameter(description = "Use the channel portion of a source IBC channel, ie. channel-5", required = false)
        @RequestParam(required = false)
        ibcSrcChannel: String?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @Parameter(
            description = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime?
    ) = transactionService.getTxsByQuery(
        msgType = msgType, txStatus = txStatus, count = count, page = page, fromDate = fromDate,
        toDate = toDate, ibcChain = ibcChain, ibcSrcPort = ibcSrcPort, ibcSrcChannel = ibcSrcChannel
    )
}
