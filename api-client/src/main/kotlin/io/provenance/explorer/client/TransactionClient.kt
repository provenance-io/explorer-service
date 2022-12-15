package io.provenance.explorer.client

import feign.Headers
import feign.Param
import feign.RequestLine
import io.provenance.explorer.model.MsgTypeSet
import io.provenance.explorer.model.TxDetails
import io.provenance.explorer.model.TxGov
import io.provenance.explorer.model.TxHeatmapRes
import io.provenance.explorer.model.TxHistoryChartData
import io.provenance.explorer.model.TxMessage
import io.provenance.explorer.model.TxStatus
import io.provenance.explorer.model.TxSummary
import io.provenance.explorer.model.TxType
import io.provenance.explorer.model.base.DateTruncGranularity
import io.provenance.explorer.model.base.PagedResults
import org.joda.time.DateTime

object TransactionRoutes {
    const val TX_V2 = "${BaseRoutes.V2_BASE}/txs"
    const val TX_V3 = "${BaseRoutes.V3_BASE}/txs"
    const val HISTORY = "$TX_V3/history"
    const val RECENT = "$TX_V2/recent"
    const val TX = "$TX_V2/{hash}"
    const val TX_MSGS = "$TX_V2/{hash}/msgs"
    const val TX_JSON = "$TX_V2/{hash}/json"
    const val TX_TYPES = "$TX_V3/{hash}/types"
    const val TXS_AT_HEIGHT = "$TX_V2/height/{height}"
    const val HEATMAP = "$TX_V2/heatmap"
    const val TYPES = "$TX_V2/types"
    const val TYPES_BY_MODULE = "$TX_V2/types/{module}"
    const val TXS_BY_MODULE = "$TX_V2/module/{module}"
    const val TXS_BY_ADDRESS = "$TX_V2/address/{address}"
    const val TXS_BY_NFT = "$TX_V2/nft/{nftAddr}"
    const val TXS_BY_MODULE_GOV = "$TX_V2/module/gov"
    const val TXS_BY_MODULE_SMART_CONTRACT = "$TX_V2/module/smart_contract"
    const val TXS_BY_IBC_CHAIN = "$TX_V2/ibc/chain/{ibcChain}"
}

@Headers(BaseClient.CT_JSON)
interface TransactionClient : BaseClient {

    @RequestLine("GET ${TransactionRoutes.HISTORY}")
    fun history(
        @Param("fromDate") fromDate: DateTime? = null,
        @Param("toDate") toDate: DateTime? = null,
        @Param("granularity") granularity: DateTruncGranularity = DateTruncGranularity.DAY
    ): List<TxHistoryChartData>

    @RequestLine("GET ${TransactionRoutes.RECENT}")
    fun recentTxs(
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1,
        @Param("msgType") msgType: String? = null,
        @Param("txStatus") txStatus: TxStatus? = null,
        @Param("fromDate") fromDate: DateTime? = null,
        @Param("toDate") toDate: DateTime? = null
    ): PagedResults<TxSummary>

    @RequestLine("GET ${TransactionRoutes.TX}")
    fun tx(@Param("hash") hash: String, @Param("blockHeight") blockHeight: Int? = null): TxDetails

    @RequestLine("GET ${TransactionRoutes.TX_MSGS}")
    fun txMsgs(
        @Param("hash") hash: String,
        @Param("blockHeight") blockHeight: Int? = null,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1,
        @Param("msgType") msgType: String? = null
    ): PagedResults<TxMessage>

    @RequestLine("GET ${TransactionRoutes.TX_JSON}")
    fun txJson(@Param("hash") hash: String, @Param("blockHeight") blockHeight: Int? = null): String

    @RequestLine("GET ${TransactionRoutes.TX_TYPES}")
    fun txTypes(@Param("hash") hash: String, @Param("blockHeight") blockHeight: Int? = null): List<TxType>

    @RequestLine("GET ${TransactionRoutes.TXS_AT_HEIGHT}")
    fun txsAtHeight(
        @Param("blockHeight") blockHeight: Int,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<TxSummary>

    @RequestLine("GET ${TransactionRoutes.HEATMAP}")
    fun heatmap(): TxHeatmapRes

    @RequestLine("GET ${TransactionRoutes.TYPES}")
    fun types(): List<TxType>

    @RequestLine("GET ${TransactionRoutes.TYPES_BY_MODULE}")
    fun typesByModule(@Param("module") module: MsgTypeSet): List<TxType>

    @RequestLine("GET ${TransactionRoutes.TXS_BY_MODULE}")
    fun txsByModule(
        @Param("module") module: MsgTypeSet,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1,
        @Param("msgType") msgType: String? = null,
        @Param("txStatus") txStatus: TxStatus? = null,
        @Param("address") address: String? = null,
        @Param("denom") denom: String? = null,
        @Param("nftAddr") nftAddr: String? = null,
        @Param("ibcChain") ibcChain: String? = null,
        @Param("ibcSrcPort") ibcSrcPort: String? = null,
        @Param("ibcSrcChannel") ibcSrcChannel: String? = null,
        @Param("fromDate") fromDate: DateTime? = null,
        @Param("toDate") toDate: DateTime? = null
    ): PagedResults<TxSummary>

    @RequestLine("GET ${TransactionRoutes.TXS_BY_ADDRESS}")
    fun txsByAddress(
        @Param("address") address: String,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1,
        @Param("msgType") msgType: String? = null,
        @Param("txStatus") txStatus: TxStatus? = null,
        @Param("fromDate") fromDate: DateTime? = null,
        @Param("toDate") toDate: DateTime? = null
    ): PagedResults<TxSummary>

    @RequestLine("GET ${TransactionRoutes.TXS_BY_NFT}")
    fun txsByNft(
        @Param("nftAddr") nftAddr: String,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1,
        @Param("msgType") msgType: String? = null,
        @Param("txStatus") txStatus: TxStatus? = null,
        @Param("fromDate") fromDate: DateTime? = null,
        @Param("toDate") toDate: DateTime? = null
    ): PagedResults<TxSummary>

    @RequestLine("GET ${TransactionRoutes.TXS_BY_MODULE_GOV}")
    fun txsByModuleGov(
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1,
        @Param("address") address: String? = null,
        @Param("msgType") msgType: String? = null,
        @Param("txStatus") txStatus: TxStatus? = null,
        @Param("fromDate") fromDate: DateTime? = null,
        @Param("toDate") toDate: DateTime? = null
    ): PagedResults<TxGov>

    @RequestLine("GET ${TransactionRoutes.TXS_BY_MODULE_SMART_CONTRACT}")
    fun txsByModuleSmartContract(
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1,
        @Param("code") code: Int? = null,
        @Param("contract") contract: String? = null,
        @Param("msgType") msgType: String? = null,
        @Param("txStatus") txStatus: TxStatus? = null,
        @Param("fromDate") fromDate: DateTime? = null,
        @Param("toDate") toDate: DateTime? = null
    ): PagedResults<TxGov>

    @RequestLine("GET ${TransactionRoutes.TXS_BY_IBC_CHAIN}")
    fun txsByIbcChain(
        @Param("ibcChain") ibcChain: String,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1,
        @Param("msgType") msgType: String? = null,
        @Param("txStatus") txStatus: TxStatus? = null,
        @Param("ibcSrcPort") ibcSrcPort: String? = null,
        @Param("ibcSrcChannel") ibcSrcChannel: String? = null,
        @Param("fromDate") fromDate: DateTime? = null,
        @Param("toDate") toDate: DateTime? = null
    ): PagedResults<TxSummary>
}
