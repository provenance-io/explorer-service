package io.provenance.explorer.client

import feign.Headers
import feign.Param
import feign.RequestLine
import io.provenance.explorer.model.ChainAum
import io.provenance.explorer.model.ChainMarketRate
import io.provenance.explorer.model.ChainPrefix
import io.provenance.explorer.model.ChainUpgrade
import io.provenance.explorer.model.GasStats
import io.provenance.explorer.model.MarketRateAvg
import io.provenance.explorer.model.MsgBasedFee
import io.provenance.explorer.model.Params
import io.provenance.explorer.model.Spotlight
import io.provenance.explorer.model.TxGasVolume
import io.provenance.explorer.model.base.DateTruncGranularity
import java.time.LocalDate

object GeneralRoutes {
    const val GENERAL_V2 = BaseRoutes.V2_BASE
    const val PARAMS = "$GENERAL_V2/params"
    const val SPOTLIGHT = "$GENERAL_V2/spotlight"
    const val GAS_STATS = "$GENERAL_V2/gas/stats"
    const val GAS_VOLUME = "$GENERAL_V2/gas/volume"
    const val CHAIN = "$GENERAL_V2/chain"
    const val CHAIN_ID = "$CHAIN/id"
    const val CHAIN_MARKET_RATE_PERIOD = "$CHAIN/market_rate/period"
    const val CHAIN_MARKET_RATE = "$CHAIN/market_rate"
    const val CHAIN_UPGRADES = "$CHAIN/upgrades"
    const val CHAIN_PREFIXES = "$CHAIN/prefixes"
    const val CHAIN_MSG_BASED_FEES = "$CHAIN/msg_based_fees"
    const val CHAIN_AUM_LIST = "$CHAIN/aum/list"
}

@Headers(BaseClient.CT_JSON)
interface GeneralClient : BaseClient {

    @RequestLine("GET ${GeneralRoutes.PARAMS}")
    fun params(): Params

    @RequestLine("GET ${GeneralRoutes.SPOTLIGHT}")
    fun spotlight(): Spotlight?

    @RequestLine("GET ${GeneralRoutes.GAS_STATS}?fromDate={fromDate}&toDate={toDate}&granularity={granularity}&msgType={msgType}")
    fun gasStats(
        @Param("fromDate") fromDate: LocalDate,
        @Param("toDate") toDate: LocalDate,
        @Param("granularity") granularity: DateTruncGranularity = DateTruncGranularity.DAY,
        @Param("msgType") msgType: String? = null
    ): List<GasStats>

    @RequestLine("GET ${GeneralRoutes.GAS_VOLUME}?fromDate={fromDate}&toDate={toDate}&granularity={granularity}")
    fun gasVolume(
        @Param("fromDate") fromDate: LocalDate,
        @Param("toDate") toDate: LocalDate,
        @Param("granularity") granularity: DateTruncGranularity = DateTruncGranularity.DAY
    ): List<TxGasVolume>

    @RequestLine("GET ${GeneralRoutes.CHAIN_ID}")
    fun chainId(): String

    @RequestLine("GET ${GeneralRoutes.CHAIN_MARKET_RATE_PERIOD}?fromDate={fromDate}&toDate={toDate}&dayCount={dayCount}")
    fun marketRateOverTime(
        @Param("fromDate") fromDate: LocalDate,
        @Param("toDate") toDate: LocalDate,
        @Param("dayCount") dayCount: Int = 14
    ): List<ChainMarketRate>

    @RequestLine("GET ${GeneralRoutes.CHAIN_MARKET_RATE}?blockCount={blockCount}")
    fun avgMarketRate(@Param("blockCount") blockCount: Int = 500): MarketRateAvg

    @RequestLine("GET ${GeneralRoutes.CHAIN_UPGRADES}")
    fun upgrades(): List<ChainUpgrade>

    @RequestLine("GET ${GeneralRoutes.CHAIN_PREFIXES}")
    fun prefixes(): List<ChainPrefix>

    @RequestLine("GET ${GeneralRoutes.CHAIN_MSG_BASED_FEES}")
    fun msgBasedFees(): List<MsgBasedFee>

    @RequestLine("GET ${GeneralRoutes.CHAIN_AUM_LIST}?fromDate={fromDate}&toDate={toDate}&dayCount={dayCount}")
    fun aumOverTime(
        @Param("fromDate") fromDate: LocalDate? = null,
        @Param("toDate") toDate: LocalDate? = null,
        @Param("dayCount") dayCount: Int = 14
    ): List<ChainAum>
}
