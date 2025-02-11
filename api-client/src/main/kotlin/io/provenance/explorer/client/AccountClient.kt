package io.provenance.explorer.client

import feign.Headers
import feign.Param
import feign.RequestLine
import io.provenance.explorer.client.BaseRoutes.PAGE_PARAMETERS
import io.provenance.explorer.model.AccountDetail
import io.provenance.explorer.model.AccountFlags
import io.provenance.explorer.model.AccountRewards
import io.provenance.explorer.model.AccountVestingInfo
import io.provenance.explorer.model.Delegation
import io.provenance.explorer.model.DenomBalanceBreakdown
import io.provenance.explorer.model.UnpaginatedDelegation
import io.provenance.explorer.model.base.CoinStr
import io.provenance.explorer.model.base.DateTruncGranularity
import io.provenance.explorer.model.base.PagedResults
import io.provenance.explorer.model.base.PeriodInSeconds
import io.provenance.explorer.model.download.TxHistoryChartData
import java.time.LocalDate

object AccountRoutes {
    const val ACCOUNTS_V2 = "${BaseRoutes.V2_BASE}/accounts"
    const val ACCOUNTS_V3 = "${BaseRoutes.V3_BASE}/accounts"
    const val ACCOUNT = "$ACCOUNTS_V2/{address}"
    const val DELEGATIONS = "$ACCOUNTS_V2/{address}/delegations"
    const val UNBONDING = "$ACCOUNTS_V2/{address}/unbonding"
    const val REDELEGATIONS = "$ACCOUNTS_V2/{address}/redelegations"
    const val REWARDS = "$ACCOUNTS_V2/{address}/rewards"
    const val BALANCES = "$ACCOUNTS_V3/{address}/balances"
    const val VESTING = "$ACCOUNTS_V3/{address}/vesting"
    const val BALANCES_AT_HEIGHT = "$ACCOUNTS_V3/{address}/balances_at_height"
    const val FEEPAYER_HISTORY = "$ACCOUNTS_V3/{address}/tx_history"
    const val BALANCES_BY_DENOM = "$ACCOUNTS_V3/{address}/balances/{denom}"
    const val BALANCES_BY_UTILITY_TOKEN = "$ACCOUNTS_V3/{address}/balances/utility_token"
    const val FLAGS = "$ACCOUNTS_V3/{address}/flags"
}

@Headers(BaseClient.CT_JSON)
interface AccountClient : BaseClient {

    @RequestLine("GET ${AccountRoutes.ACCOUNT}")
    fun account(@Param("address") address: String): AccountDetail

    @RequestLine("GET ${AccountRoutes.DELEGATIONS}?$PAGE_PARAMETERS")
    fun delegations(
        @Param("address") address: String,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<Delegation>

    @RequestLine("GET ${AccountRoutes.UNBONDING}")
    fun unbondings(@Param("address") address: String): UnpaginatedDelegation

    @RequestLine("GET ${AccountRoutes.REDELEGATIONS}")
    fun redelegations(@Param("address") address: String): UnpaginatedDelegation

    @RequestLine("GET ${AccountRoutes.REWARDS}")
    fun rewards(@Param("address") address: String): AccountRewards

    @RequestLine("GET ${AccountRoutes.BALANCES}?$PAGE_PARAMETERS")
    fun balances(
        @Param("address") address: String,
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1
    ): PagedResults<DenomBalanceBreakdown>

    @RequestLine("GET ${AccountRoutes.VESTING}?continuousPeriod={continuousPeriod}")
    fun vestingSchedule(
        @Param("address") address: String,
        @Param("continuousPeriod") continuousPeriod: PeriodInSeconds = PeriodInSeconds.DAY
    ): AccountVestingInfo

    @RequestLine("GET ${AccountRoutes.BALANCES_AT_HEIGHT}?height={height}&denom={denom}")
    fun balancesAtHeight(
        @Param("address") address: String,
        @Param("height") height: Int,
        @Param("denom") denom: String? = null
    ): List<CoinStr>

    @RequestLine("GET ${AccountRoutes.FEEPAYER_HISTORY}?fromDate={fromDate}&toDate={toDate}&granularity={granularity}")
    fun feepayerHistory(
        @Param("address") address: String,
        @Param("fromDate") fromDate: LocalDate? = null,
        @Param("toDate") toDate: LocalDate? = null,
        @Param("granularity") granularity: DateTruncGranularity? = DateTruncGranularity.DAY
    ): List<TxHistoryChartData>

    @RequestLine("GET ${AccountRoutes.BALANCES_BY_DENOM}")
    fun balanceByDenom(
        @Param("address") address: String,
        @Param("denom") denom: String
    ): DenomBalanceBreakdown

    @RequestLine("GET ${AccountRoutes.BALANCES_BY_UTILITY_TOKEN}")
    fun balanceByUtilityToken(@Param("address") address: String): DenomBalanceBreakdown

    @RequestLine("GET ${AccountRoutes.FLAGS}")
    fun flags(@Param("address") address: String): AccountFlags
}
