package io.provenance.explorer.client

import feign.Headers
import feign.Param
import feign.RequestLine
import io.provenance.explorer.model.BlockLatencyData
import io.provenance.explorer.model.Delegation
import io.provenance.explorer.model.MarketRateAvg
import io.provenance.explorer.model.MissedBlocksTimeframe
import io.provenance.explorer.model.UnpaginatedDelegation
import io.provenance.explorer.model.UptimeDataSet
import io.provenance.explorer.model.ValidatorAtHeight
import io.provenance.explorer.model.ValidatorCommission
import io.provenance.explorer.model.ValidatorCommissionHistory
import io.provenance.explorer.model.ValidatorDetails
import io.provenance.explorer.model.ValidatorMarketRate
import io.provenance.explorer.model.ValidatorState
import io.provenance.explorer.model.ValidatorSummary
import io.provenance.explorer.model.ValidatorSummaryAbbrev
import io.provenance.explorer.model.base.PagedResults
import io.provenance.explorer.model.base.Timeframe
import org.joda.time.DateTime

object ValidatorRoutes {
    const val VALIDATOR_V2 = "${BaseRoutes.V2_BASE}/validators"
    const val VALIDATOR_V3 = "${BaseRoutes.V3_BASE}/validators"
    const val RECENT = "$VALIDATOR_V3/recent"
    const val VALIDATORS_AT_HEIGHT = "$VALIDATOR_V2/height/{blockHeight}"
    const val RECENT_ABBREV = "$VALIDATOR_V2/recent/abbrev"
    const val VALIDATOR = "$VALIDATOR_V2/{address}"
    const val VALIDATOR_BONDED = "$VALIDATOR_V2/{address}/delegations/bonded"
    const val VALIDATOR_UNBONDING = "$VALIDATOR_V2/{address}/delegations/unbonding"
    const val VALIDATOR_COMMISSION = "$VALIDATOR_V2/{address}/commission"
    const val VALIDATOR_COMMISSION_HISTORY = "$VALIDATOR_V2/{address}/commission/history"
    const val VALIDATOR_MARKET_RATE = "$VALIDATOR_V2/{address}/market_rate"
    const val VALIDATOR_MARKET_RATE_PERIOD = "$VALIDATOR_V2/{address}/market_rate/period"
    const val VALIDATOR_LATENCY = "$VALIDATOR_V2/{address}/latency"
    const val MISSED_BLOCKS_DISTINCT = "$VALIDATOR_V2/missed_blocks/distinct"
    const val MISSED_BLOCKS = "$VALIDATOR_V2/missed_blocks"
    const val UPTIME = "$VALIDATOR_V2/uptime"
}

@Headers(BaseClient.CT_JSON)
interface ValidatorClient : BaseClient {

    @RequestLine("GET ${ValidatorRoutes.RECENT}")
    fun recentValidators(
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1,
        @Param("status") status: ValidatorState = ValidatorState.ACTIVE
    ): PagedResults<ValidatorSummary>

    @RequestLine("GET ${ValidatorRoutes.VALIDATORS_AT_HEIGHT}")
    fun validatorsAtHeight(
        @Param("blockHeight") blockHeight: Int,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<ValidatorAtHeight>

    @RequestLine("GET ${ValidatorRoutes.RECENT_ABBREV}")
    fun allValidatorsAbbreviated(): PagedResults<ValidatorSummaryAbbrev>

    @RequestLine("GET ${ValidatorRoutes.VALIDATOR}")
    fun validator(@Param("address") address: String): ValidatorDetails

    @RequestLine("GET ${ValidatorRoutes.VALIDATOR_BONDED}")
    fun validatorBondedDelegations(
        @Param("address") address: String,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<Delegation>

    @RequestLine("GET ${ValidatorRoutes.VALIDATOR_UNBONDING}")
    fun validatorUnbondingDelegations(@Param("address") address: String): UnpaginatedDelegation

    @RequestLine("GET ${ValidatorRoutes.VALIDATOR_COMMISSION}")
    fun validatorCommission(@Param("address") address: String): ValidatorCommission

    @RequestLine("GET ${ValidatorRoutes.VALIDATOR_COMMISSION_HISTORY}")
    fun validatorCommissionHistory(@Param("address") address: String): ValidatorCommissionHistory

    @RequestLine("GET ${ValidatorRoutes.VALIDATOR_MARKET_RATE}")
    fun validatorAvgMarketRate(
        @Param("address") address: String,
        @Param("txCount") txCount: Int = 500
    ): MarketRateAvg

    @RequestLine("GET ${ValidatorRoutes.VALIDATOR_MARKET_RATE_PERIOD}")
    fun validatorMarketRateOverTime(
        @Param("address") address: String,
        @Param("fromDate") fromDate: DateTime? = null,
        @Param("toDate") toDate: DateTime? = null,
        @Param("dayCount") dayCount: Int = 14
    ): List<ValidatorMarketRate>

    @RequestLine("GET ${ValidatorRoutes.VALIDATOR_LATENCY}")
    fun validatorLatency(
        @Param("address") address: String,
        @Param("blockCount") blockCount: Int = 100
    ): BlockLatencyData

    @RequestLine("GET ${ValidatorRoutes.MISSED_BLOCKS_DISTINCT}")
    fun distinctValidatorsWithMissedBlocks(
        @Param("timeframe") timeframe: Timeframe = Timeframe.HOUR
    ): MissedBlocksTimeframe

    @RequestLine("GET ${ValidatorRoutes.MISSED_BLOCKS}")
    fun validatorsWithMissedBocks(
        @Param("timeframe") timeframe: Timeframe = Timeframe.HOUR,
        @Param("validatorAddr") validatorAddr: String
    ): MissedBlocksTimeframe

    @RequestLine("GET ${ValidatorRoutes.UPTIME}")
    fun uptimeData(): UptimeDataSet
}
