package io.provenance.explorer.client

import feign.Headers
import feign.Param
import feign.RequestLine
import io.provenance.explorer.model.CmcHistoricalQuote
import io.provenance.explorer.model.CmcLatestDataAbbrev
import io.provenance.explorer.model.RichAccount
import io.provenance.explorer.model.TokenDistribution
import io.provenance.explorer.model.TokenSupply
import java.time.LocalDate
import java.math.BigDecimal

object TokenRoutes {
    const val TOKEN_V3 = "${BaseRoutes.V3_BASE}/utility_token"
    const val STATS = "$TOKEN_V3/stats"
    const val DISTRIBUTION = "$TOKEN_V3/distribution"
    const val RICH_LIST = "$TOKEN_V3/rich_list"
    const val MAX_SUPPLY = "$TOKEN_V3/max_supply"
    const val TOTAL_SUPPLY = "$TOKEN_V3/total_supply"
    const val CIRCULATING_SUPPLY = "$TOKEN_V3/circulating_supply"
    const val HISTORICAL_PRICING = "$TOKEN_V3/historical_pricing"
    const val LATEST_PRICING = "$TOKEN_V3/latest_pricing"
}

@Headers(BaseClient.CT_JSON)
interface TokenClient : BaseClient {

    @RequestLine("GET ${TokenRoutes.STATS}")
    fun stats(): TokenSupply

    @RequestLine("GET ${TokenRoutes.DISTRIBUTION}")
    fun distribution(): List<TokenDistribution>

    @RequestLine("GET ${TokenRoutes.RICH_LIST}?limit={limit}")
    fun richList(@Param("limit") limit: Int): List<RichAccount>

    @RequestLine("GET ${TokenRoutes.MAX_SUPPLY}")
    fun maxSupply(): BigDecimal

    @RequestLine("GET ${TokenRoutes.TOTAL_SUPPLY}")
    fun totalSupply(): BigDecimal

    @RequestLine("GET ${TokenRoutes.CIRCULATING_SUPPLY}")
    fun circulatingSupply(): BigDecimal

    @RequestLine("GET ${TokenRoutes.HISTORICAL_PRICING}?fromDate={fromDate}&toDate={toDate}")
    fun historicalPricing(
        @Param("fromDate") fromDate: LocalDate? = null,
        @Param("toDate") toDate: LocalDate? = null
    ): List<CmcHistoricalQuote>

    @RequestLine("GET ${TokenRoutes.LATEST_PRICING}")
    fun latestPricing(): CmcLatestDataAbbrev?
}
