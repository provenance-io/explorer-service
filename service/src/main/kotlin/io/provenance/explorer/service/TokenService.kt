package io.provenance.explorer.service

import com.fasterxml.jackson.module.kotlin.readValue
import cosmos.auth.v1beta1.Auth
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.provenance.explorer.KTOR_CLIENT_JAVA
import io.provenance.explorer.VANILLA_MAPPER
import io.provenance.explorer.config.ExplorerProperties.Companion.PROV_ACC_PREFIX
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN_BASE_MULTIPLIER
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.CacheKeys
import io.provenance.explorer.domain.entities.CacheUpdateRecord
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.MarkerUnitRecord
import io.provenance.explorer.domain.entities.TokenDistributionAmountsRecord
import io.provenance.explorer.domain.entities.TokenDistributionPaginatedResultsRecord
import io.provenance.explorer.domain.entities.TokenHistoricalDailyRecord
import io.provenance.explorer.domain.entities.addressList
import io.provenance.explorer.domain.entities.vestingAccountTypes
import io.provenance.explorer.domain.exceptions.validate
import io.provenance.explorer.domain.extensions.CsvData
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.roundWhole
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.toPercentage
import io.provenance.explorer.domain.models.HistoricalPrice
import io.provenance.explorer.domain.models.OsmosisApiResponse
import io.provenance.explorer.domain.models.OsmosisHistoricalPrice
import io.provenance.explorer.domain.models.explorer.TokenHistoricalDataRequest
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provenance.explorer.model.AssetHolder
import io.provenance.explorer.model.CmcLatestDataAbbrev
import io.provenance.explorer.model.RichAccount
import io.provenance.explorer.model.TokenDistribution
import io.provenance.explorer.model.TokenDistributionAmount
import io.provenance.explorer.model.TokenSupply
import io.provenance.explorer.model.base.CoinStr
import io.provenance.explorer.model.base.CountStrTotal
import io.provenance.explorer.model.base.PagedResults
import io.provlabs.flow.api.NavEvent
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.servlet.ServletOutputStream

@Service
class TokenService(private val accountClient: AccountGrpcClient, private val flowApiGrpcClient: FlowApiGrpcClient) {

    protected val logger = logger(TokenService::class)

    fun getTokenDistributionStats() = transaction { TokenDistributionAmountsRecord.getStats() }

    fun saveResults(records: List<AssetHolder>) = runBlocking {
        records.asFlow().map {
            AccountRecord.saveAccount(
                it.ownerAddress,
                PROV_ACC_PREFIX,
                accountClient.getAccountInfo(it.ownerAddress)
            )
        }.collect()
        TokenDistributionPaginatedResultsRecord.savePaginatedResults(records)
    }

    fun updateTokenDistributionStats(denom: String) {
        val pageResults = getAssetHoldersWithRetry(denom, 1, 10)
        saveResults(pageResults.results)
        if (pageResults.pages > 1) {
            for (i in 2..pageResults.pages) {
                val results = getAssetHoldersWithRetry(denom, i, 10)
                saveResults(results.results)
            }
        }
        // calculate ranks
        calculateTokenDistributionStats()
    }

    // 1st requests that take longer than expected result in a
    // DEADLINE_EXCEEDED error. Add retry functionality to give
    // a chance to succeed.
    private fun getAssetHoldersWithRetry(
        denom: String,
        page: Int,
        count: Int,
        retryCount: Int = 3
    ): PagedResults<AssetHolder> {
        var hasSucceeded = false
        var numberOfTriesRemaining = retryCount
        var assetHolders: PagedResults<AssetHolder>? = null
        while (!hasSucceeded && numberOfTriesRemaining > 0) {
            numberOfTriesRemaining--
            assetHolders = getAssetHolders(denom, page, count)
            hasSucceeded = assetHolders.results.isNotEmpty()
        }

        return assetHolders!!
    }

    private fun getAssetHolders(denom: String, page: Int, count: Int) = runBlocking {
        val unit = MarkerUnitRecord.findByUnit(denom)?.marker ?: denom
        val supply = maxSupply().toString()
        val res = accountClient.getDenomHolders(unit, page.toOffset(count), count)
        val list = res.denomOwnersList.asFlow().map { bal ->
            AssetHolder(bal.address, CountStrTotal(bal.balance.amount, supply, unit))
        }.toList().sortedWith(compareBy { it.balance.count.toBigDecimal() }).asReversed()
        PagedResults(res.pagination.total.pageCountOfResults(count), list, res.pagination.total)
    }

    private fun calculateTokenDistributionStats() {
        val tokenDistributions = listOf(
            Triple(1, 0, "1"),
            Triple(1, 1, "2"),
            Triple(1, 2, "3"),
            Triple(1, 3, "4"),
            Triple(1, 4, "5"),
            Triple(5, 5, "6-10"),
            Triple(50, 10, "11-50"),
            Triple(50, 50, "51-100"),
            Triple(500, 100, "101-500"),
            Triple(500, 500, "501-1000"),
            Triple("ALL", 1000, "1001-")
        ).map { (limit, offset, range) ->
            val results = TokenDistributionPaginatedResultsRecord.findByLimitOffset(richListAccounts(), limit, offset)
            val denom = results[0].data.denom
            val totalSupply = totalSupply()
            val rangeBalance = results.sumOf { it.data.count.toBigDecimal() }
            val percentOfTotal = rangeBalance.asPercentOf(totalSupply).toPlainString()
            TokenDistribution(range, TokenDistributionAmount(denom, rangeBalance.toString()), percentOfTotal)
        }

        TokenDistributionAmountsRecord.batchUpsert(tokenDistributions)
    }

    fun getTokenBreakdown() = runBlocking {
        val bonded =
            accountClient.getStakingPool().pool.bondedTokens.toBigDecimal().roundWhole().toCoinStr(UTILITY_TOKEN)
        TokenSupply(
            maxSupply().toCoinStr(UTILITY_TOKEN),
            totalSupply().toCoinStr(UTILITY_TOKEN),
            circulatingSupply().toCoinStr(UTILITY_TOKEN),
            communityPoolSupply().toCoinStr(UTILITY_TOKEN),
            bonded,
            burnedSupply().toCoinStr(UTILITY_TOKEN)
        )
    }

    fun nhashMarkerAddr() = MarkerCacheRecord.findByDenom(UTILITY_TOKEN)?.markerAddress!!
    fun burnedSupply() =
        runBlocking { accountClient.getMarkerBalance(nhashMarkerAddr(), UTILITY_TOKEN).toBigDecimal().roundWhole() }

    fun moduleAccounts() = AccountRecord.findAccountsByType(listOf(Auth.ModuleAccount::class.java.simpleName))
    fun zeroSeqAccounts() = AccountRecord.findZeroSequenceAccounts()
    fun vestingAccounts() = AccountRecord.findAccountsByType(vestingAccountTypes)
    fun contractAccounts() = AccountRecord.findContractAccounts()
    fun allAccounts() = transaction { AccountRecord.all().toMutableList() }
    fun communityPoolSupply() =
        runBlocking { accountClient.getCommunityPoolAmount(UTILITY_TOKEN).toBigDecimal().roundWhole() }

    fun richListAccounts() =
        allAccounts().addressList() - zeroSeqAccounts().toSet() - moduleAccounts().addressList() - contractAccounts().addressList() - setOf(nhashMarkerAddr())

    fun totalBalanceForList(addresses: Set<String>) = runBlocking {
        TokenDistributionPaginatedResultsRecord.findByAddresses(addresses).asFlow()
            .map { it.data.count.toBigDecimal() }
            .toList()
            .sumOf { it }
    }

    fun totalSpendableBalanceForList(addresses: Set<String>) = runBlocking {
        addresses.asFlow()
            .map { accountClient.getSpendableBalanceDenom(it, UTILITY_TOKEN)!!.amount.toBigDecimal() }
            .toList()
            .sumOf { it }
    }

    // non-spendable = total - spendable
    fun totalNonspendableBalanceForList(addresses: Set<String>) = transaction {
        val total = totalBalanceForList(addresses)
        val spendable = totalSpendableBalanceForList(addresses)
        total - spendable
    }

    // max supply = supply from bank module
    fun maxSupply() = runBlocking { accountClient.getCurrentSupply(UTILITY_TOKEN).amount.toBigDecimal() }

    // total supply = max - burned -> comes from the nhash marker address
    fun totalSupply() = maxSupply() - burnedSupply().roundWhole()

    // circulating supply = max - burned - modules - zero seq - pool - nonspendable
    fun circulatingSupply() =
        maxSupply() // max
            .minus(burnedSupply()) // burned
            .minus(totalBalanceForList(zeroSeqAccounts().toSet() + moduleAccounts().addressList())) // modules/zero seq
            .minus(communityPoolSupply()) // pool
            .minus(totalNonspendableBalanceForList(vestingAccounts().addressList())) // nonSpendable
            .roundWhole()

    // rich list = all accounts - nhash marker - zero seq - modules - contracts ->>>>>>>>> out of total
    fun richList(topCount: Int = 100) = transaction {
        val totalSupply = totalSupply()
        TokenDistributionPaginatedResultsRecord.findByLimitOffset(richListAccounts(), topCount, 0)
            .map {
                RichAccount(
                    it.ownerAddress,
                    CoinStr(it.data.count, it.data.denom),
                    it.data.count.toPercentage(BigDecimal(100), totalSupply, 4)
                )
            }
    }
    fun getTokenHistorical(fromDate: DateTime?, toDate: DateTime?) =
        TokenHistoricalDailyRecord.findForDates(fromDate?.startOfDay(), toDate?.startOfDay())

    fun getTokenLatest() = CacheUpdateRecord.fetchCacheByKey(CacheKeys.UTILITY_TOKEN_LATEST.key)?.cacheValue?.let {
        VANILLA_MAPPER.readValue<CmcLatestDataAbbrev>(it)
    }

    fun fetchHistoricalPriceData(fromDate: DateTime?): List<HistoricalPrice> = runBlocking {
        val osmosisHistoricalPrices = fetchOsmosisData(fromDate)
        val onChainNavEvents = fetchOnChainNavData(UTILITY_TOKEN, fromDate, 17800)

        val osmosisPrices = osmosisHistoricalPrices.map { osmosisPrice ->
            HistoricalPrice(
                time = osmosisPrice.time,
                high = osmosisPrice.high,
                low = osmosisPrice.low,
                close = osmosisPrice.close,
                open = osmosisPrice.open,
                volume = osmosisPrice.volume
            )
        }

        val navPrices = onChainNavEvents.map { navEvent ->
            val pricePerHash = calculatePricePerHash(navEvent.priceAmount, navEvent.volume)
            HistoricalPrice(
                time = navEvent.blockTime,
                high = BigDecimal(pricePerHash),
                low = BigDecimal(pricePerHash),
                close = BigDecimal(pricePerHash),
                open = BigDecimal(pricePerHash),
                volume = BigDecimal(navEvent.volume)
            )
        }
        return@runBlocking osmosisPrices + navPrices
    }

    fun fetchOsmosisData(fromDate: DateTime?): List<OsmosisHistoricalPrice> = runBlocking {
        val input = buildInputQuery(fromDate, determineTimeFrame(fromDate))
        try {
            val url = """https://app.osmosis.zone/api/edge-trpc-assets/assets.getAssetHistoricalPrice?input=$input"""
            logger.info("Calling $url with fromDate $fromDate")
            val response: HttpResponse = KTOR_CLIENT_JAVA.get(url) {
                accept(ContentType.Application.Json)
            }

            val rawResponse: String = response.bodyAsText()
            logger.debug("Osmosis GET: $url Raw Response: $rawResponse")

            val osmosisApiResponse: OsmosisApiResponse = response.body()
            osmosisApiResponse.result.data.json
        } catch (e: ResponseException) {
            logger.error("Error fetching from Osmosis API: ${e.response}", e)
            emptyList()
        } catch (e: Exception) {
            logger.error("Error fetching from Osmosis API: ${e.message}", e)
            emptyList()
        }
    }

    fun fetchOnChainNavData(denom: String, fromDate: DateTime?, limit: Int = 100): List<NavEvent> = runBlocking {
        val priceDenoms = listOf("uusd.trading", "uusdc.figure.se")
        flowApiGrpcClient.getMarkerNavByPriceDenoms(denom, priceDenoms, fromDate, limit)
    }

    enum class TimeFrame(val minutes: Int) {
        FIVE_MINUTES(5),
        TWO_HOURS(120),
        ONE_DAY(1440)
    }

    /**
     * Determines the appropriate TimeFrame based on the fromDate.
     *
     * @param fromDate The starting date to determine the time frame.
     * @return The appropriate TimeFrame enum value.
     */
    fun determineTimeFrame(fromDate: DateTime?): TimeFrame {
        val now = DateTime.now(DateTimeZone.UTC)
        val duration = Duration(fromDate, now)
        return when {
            duration.standardDays <= 14 -> TimeFrame.FIVE_MINUTES
            duration.standardDays <= 60 -> TimeFrame.TWO_HOURS
            else -> TimeFrame.ONE_DAY
        }
    }

    /**
     * Builds the input query parameter for fetching historical data.
     *
     * This function constructs a URL-encoded JSON query parameter for fetching historical data based on the given
     * `fromDate` and `timeFrame`. The `timeFrame` represents the number of minutes between updates. The allowed values
     * for `timeFrame` are defined in the `TimeFrame` enum:
     * - FIVE_MINUTES: data goes back 2 weeks.
     * - TWO_HOURS: data goes back 2 months.
     * - ONE_DAY: data goes back to the beginning of time.
     *
     * The function calculates the total number of frames (`numRecentFrames`) from the `fromDate` to the current time,
     * based on the specified `timeFrame`.
     *
     * @param fromDate The starting date from which to calculate the number of frames.
     * @param timeFrame The time interval between updates, specified as a `TimeFrame` enum value.
     * @return A URL-encoded JSON string to be used as a query parameter for fetching historical data.
     */
    fun buildInputQuery(fromDate: DateTime?, timeFrame: TimeFrame): String {
        val coinDenom = "ibc/CE5BFF1D9BADA03BB5CCA5F56939392A761B53A10FBD03B37506669C3218D3B2"
        val coinMinimalDenom = "ibc/CE5BFF1D9BADA03BB5CCA5F56939392A761B53A10FBD03B37506669C3218D3B2"
        val now = DateTime.now(DateTimeZone.UTC)
        val duration = Duration(fromDate, now)
        val numRecentFrames = (duration.standardMinutes / timeFrame.minutes).toInt()
        return URLEncoder.encode(
            """{"json":{"coinDenom":"$coinDenom","coinMinimalDenom":"$coinMinimalDenom","timeFrame":{"custom":{"timeFrame":${timeFrame.minutes},"numRecentFrames":$numRecentFrames}}}}""",
            "UTF-8"
        )
    }

    fun getHashPricingDataDownload(filters: TokenHistoricalDataRequest, resp: ServletOutputStream): ZipOutputStream {
        validate(filters.datesValidation())
        val baseFileName = filters.getFileNameBase()

        val fileList = runBlocking {
            val data = fetchOsmosisData(filters.fromDate)
            listOf(
                CsvData(
                    "TokenHistoricalData",
                    filters.tokenHistoricalCsvBaseHeaders,
                    data.map { it.toCsv() }
                )
            )
        }

        val zos = ZipOutputStream(resp)
        fileList.forEach { file ->
            zos.putNextEntry(ZipEntry("$baseFileName - ${file.fileName}.csv"))
            zos.write(file.writeCsvEntry())
            zos.closeEntry()
        }
        zos.putNextEntry(ZipEntry("$baseFileName - FILTERS.txt"))
        zos.write(filters.writeFilters())
        zos.closeEntry()
        zos.close()
        return zos
    }

    private fun OsmosisHistoricalPrice.toCsv(): List<String> {
        return listOf(
            time.toString(),
            open.toString(),
            high.toString(),
            low.toString(),
            close.toString(),
            volume.toString()
        )
    }
}

/**
 * Calculates the price per hash unit based on the total price in USD (expressed as whole numbers
 * where 12345 equals $12.345 USD) and the volume in nHash (nano Hash).
 *
 * @param priceAmount The total price in whole-number USD cents (e.g., 12345 equals $12.345 USD).
 * @param volumeNhash The volume of the transaction in nHash (nano Hash).
 *                    1 Hash = 1,000,000,000 nHash.
 * @return The price per hash unit. Returns 0.0 if the volumeNhash is 0 to avoid division by zero.
 */
fun calculatePricePerHash(priceAmountMillis: Long, volumeNhash: Long): Double {
    if (volumeNhash == 0L) {
        return 0.0
    }
    val volumeHash = BigDecimal(volumeNhash).divide(UTILITY_TOKEN_BASE_MULTIPLIER, 10, RoundingMode.HALF_UP)
    val pricePerHash = BigDecimal(priceAmountMillis).divide(volumeHash, 10, RoundingMode.HALF_UP)
    return pricePerHash.divide(BigDecimal(1000), 10, RoundingMode.HALF_UP).toDouble()
}

fun BigDecimal.asPercentOf(divisor: BigDecimal): BigDecimal = this.divide(divisor, 20, RoundingMode.CEILING)
