package io.provenance.explorer.web.v2

import io.provenance.explorer.model.MsgTypeSet
import io.provenance.explorer.model.TxGov
import io.provenance.explorer.model.TxStatus
import io.provenance.explorer.model.base.PagedResults
import io.provenance.explorer.service.TransactionService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
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
import javax.validation.constraints.Max
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v2/txs"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Transaction endpoints",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Transactions"]
)
class TransactionControllerV2(private val transactionService: TransactionService) {

    @ApiOperation("Return the latest transactions with query params")
    @GetMapping("/recent")
    @Cacheable(value = ["responses"], key = "{#root.methodName, #count, #page, #msgType, #txStatus, #fromDate, #toDate}")
    fun txsRecent(
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 200", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @ApiParam(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @ApiParam(required = false)
        @RequestParam(required = false)
        txStatus: TxStatus?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
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

    @ApiOperation("Return transaction detail by hash value")
    @GetMapping("/{hash}")
    fun txByHash(
        @PathVariable hash: String,
        @ApiParam(required = false)
        @RequestParam(required = false)
        blockHeight: Int? = null
    ) = transactionService.getTransactionByHash(hash, blockHeight)

    @ApiOperation("Return a transaction's messages by tx hash value")
    @GetMapping("/{hash}/msgs")
    fun txMsgsByHash(
        @PathVariable hash: String,
        @ApiParam(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 200", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @ApiParam(required = false)
        @RequestParam(required = false)
        blockHeight: Int? = null
    ) = transactionService.getTxMsgsPaginated(hash, msgType, page, count, blockHeight)

    @ApiOperation("Returns a transaction object as JSON by tx hash value")
    @GetMapping("/{hash}/json")
    fun transactionJson(
        @PathVariable hash: String,
        @ApiParam(required = false)
        @RequestParam(required = false)
        blockHeight: Int? = null
    ) = transactionService.getTransactionJson(hash, blockHeight)

    @ApiOperation("Returns transactions by block height")
    @GetMapping("/height/{height}")
    fun txByBlockHeight(
        @PathVariable height: Int,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 200", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int
    ) = transactionService.getTxsByQuery(txHeight = height, count = count, page = page)

    @ApiOperation("Returns a heatmap of transaction activity on chain")
    @GetMapping("/heatmap")
    @Deprecated("Use /api/v3/txs/heatmap")
    @java.lang.Deprecated
    fun txHeatmap() = transactionService.getTxHeatmap()

    @ApiOperation("Return list of transaction types")
    @GetMapping("/types")
    fun txTypes() = transactionService.getTxTypes(null)

    @ApiOperation("Return list of transaction types by Module")
    @GetMapping("/types/{module}")
    fun txTypesByModule(@PathVariable module: MsgTypeSet) = transactionService.getTxTypes(module)

    @ApiOperation("Return list of transaction types by tx hash")
    @GetMapping("/types/tx/{hash}")
    @Deprecated("Use /api/v3/txs/{hash}/types")
    @java.lang.Deprecated
    fun txTypesByTxHash(
        @PathVariable hash: String,
        @ApiParam(required = false)
        @RequestParam(required = false)
        blockHeight: Int? = null
    ) = transactionService.getTxTypesByTxHash(hash)

    @ApiOperation("Returns transactions by query params for a specific module of msg types")
    @GetMapping("/module/{module}")
    fun txsByModule(
        @PathVariable module: MsgTypeSet,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 200", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @ApiParam(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @ApiParam(required = false)
        @RequestParam(required = false)
        txStatus: TxStatus?,
        @ApiParam(value = "Use either the standard address or a validator operator address", required = false)
        @RequestParam(required = false)
        address: String?,
        @ApiParam(value = "Use any of the denom units to search, ie `nhash` or `hash`", required = false)
        @RequestParam(required = false)
        denom: String?,
        @ApiParam(value = "Use the nft address type, ie. scope, scopespec, or the scope UUID", required = false)
        @RequestParam(required = false)
        nftAddr: String?,
        @ApiParam(value = "Use the chain identifying name, ie. osmosis-1", required = false)
        @RequestParam(required = false)
        ibcChain: String?,
        @ApiParam(value = "Use the port portion of a source IBC channel, ie. transfer", required = false)
        @RequestParam(required = false)
        ibcSrcPort: String?,
        @ApiParam(value = "Use the channel portion of a source IBC channel, ie. channel-5", required = false)
        @RequestParam(required = false)
        ibcSrcChannel: String?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime?
    ) = transactionService.getTxsByQuery(
        address, denom, module, msgType, null, txStatus, count, page, fromDate, toDate, nftAddr,
        ibcChain, ibcSrcPort, ibcSrcChannel
    )

    @ApiOperation("Returns transactions by query params for a specific address")
    @GetMapping("/address/{address}")
    fun txsByAddress(
        @ApiParam(value = "Use either the standard address or a validator operator address")
        @PathVariable
        address: String,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 200", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @ApiParam(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @ApiParam(required = false)
        @RequestParam(required = false)
        txStatus: TxStatus?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
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

    @ApiOperation("Returns transactions by query params for a specific nft address")
    @GetMapping("/nft/{nftAddr}")
    fun txsByNftAddress(
        @ApiParam(value = "Use the nft address type, ie. scope, scopespec, or the scope UUID")
        @PathVariable
        nftAddr: String,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 200", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @ApiParam(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @ApiParam(required = false)
        @RequestParam(required = false)
        txStatus: TxStatus?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
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

    @ApiOperation("Returns transactions for governance module with unique response type")
    @GetMapping("/module/gov")
    fun txsForGovernance(
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 200", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @ApiParam(value = "Use either the standard address or a validator operator address", required = false)
        @RequestParam(required = false)
        address: String?,
        @ApiParam(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @ApiParam(required = false)
        @RequestParam(required = false)
        txStatus: TxStatus?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime?
    ) = PagedResults<TxGov>(0, emptyList(), 0)
//        transactionService.getGovernanceTxs(address, msgType, txStatus, page, count, fromDate, toDate)

    @ApiOperation("Returns transactions for smart contract module with unique response type")
    @GetMapping("/module/smart_contract")
    fun txsForSmartContracts(
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 200", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @ApiParam(value = "The base code ID", required = false)
        @RequestParam(required = false)
        code: Int?,
        @ApiParam(value = "The contract address", required = false)
        @RequestParam(required = false)
        contract: String?,
        @ApiParam(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @ApiParam(required = false)
        @RequestParam(required = false)
        txStatus: TxStatus?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        toDate: DateTime?
    ) = transactionService.getSmartContractTxs(code, contract, msgType, txStatus, page, count, fromDate, toDate)

    @ApiOperation("Returns transactions for the IBC module with standard response type")
    @GetMapping("/ibc/chain/{ibcChain}")
    fun txsForIbc(
        @ApiParam(value = "Use the chain identifying name, ie. osmosis-1")
        @PathVariable
        ibcChain: String,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 200", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @ApiParam(required = false)
        @RequestParam(required = false)
        msgType: String?,
        @ApiParam(required = false)
        @RequestParam(required = false)
        txStatus: TxStatus?,
        @ApiParam(value = "Use the port portion of a source IBC channel, ie. transfer", required = false)
        @RequestParam(required = false)
        ibcSrcPort: String?,
        @ApiParam(value = "Use the channel portion of a source IBC channel, ie. channel-5", required = false)
        @RequestParam(required = false)
        ibcSrcChannel: String?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        )
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: DateTime?,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
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
