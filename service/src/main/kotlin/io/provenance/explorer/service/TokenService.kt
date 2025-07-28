package io.provenance.explorer.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import cosmos.auth.v1beta1.Auth
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
import io.provenance.explorer.domain.extensions.percentChange
import io.provenance.explorer.domain.extensions.roundWhole
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.toPercentage
import io.provenance.explorer.domain.extensions.toThirdDecimal
import io.provenance.explorer.domain.models.HistoricalPrice
import io.provenance.explorer.domain.models.explorer.TokenHistoricalDataRequest
import io.provenance.explorer.domain.models.toCsv
import io.provenance.explorer.grpc.extensions.isVesting
import io.provenance.explorer.grpc.extensions.toVestingData
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provenance.explorer.model.AssetHolder
import io.provenance.explorer.model.CmcHistoricalQuote
import io.provenance.explorer.model.CmcLatestDataAbbrev
import io.provenance.explorer.model.CmcLatestQuoteAbbrev
import io.provenance.explorer.model.CmcQuote
import io.provenance.explorer.model.RichAccount
import io.provenance.explorer.model.TokenDistribution
import io.provenance.explorer.model.TokenDistributionAmount
import io.provenance.explorer.model.TokenSupply
import io.provenance.explorer.model.base.CoinStr
import io.provenance.explorer.model.base.CountStrTotal
import io.provenance.explorer.model.base.PagedResults
import io.provenance.explorer.model.base.PeriodInSeconds
import io.provenance.explorer.model.base.USD_UPPER
import io.provenance.explorer.service.pricing.fetchers.HistoricalPriceFetcher
import io.provenance.explorer.service.pricing.fetchers.HistoricalPriceFetcherFactory
import jakarta.servlet.ServletOutputStream
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Service
class TokenService(
    private val accountClient: AccountGrpcClient,
    private val historicalPriceFetcherFactory: HistoricalPriceFetcherFactory
) {
    @Autowired
    lateinit var objectMapper: ObjectMapper

    protected val logger = logger(TokenService::class)

    protected val historicalPriceFetchers: List<HistoricalPriceFetcher> by lazy {
        historicalPriceFetcherFactory.createNhashPricingFetchers()
    }

    fun getTokenDistributionStats() = transaction { TokenDistributionAmountsRecord.getStats() }

    fun saveResults(records: List<AssetHolder>) = runBlocking {
        records.asFlow().map {
            try {
                AccountRecord.saveAccount(
                    it.ownerAddress,
                    PROV_ACC_PREFIX,
                    accountClient.getAccountInfo(it.ownerAddress)
                )
            } catch (e: Exception) {
                logger.error("Unable to save account for ${it.ownerAddress}", e)
            }
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

    fun getAssetHolders(denom: String, page: Int, count: Int) = runBlocking {
        val unit = MarkerUnitRecord.findByUnit(denom)?.marker ?: denom
        val supply = maxSupply(unit).toString()
        val res = accountClient.getDenomHolders(unit, page.toOffset(count), count)
        val list = res.denomOwnersList.asFlow().map { bal ->
            val spendableAmount = accountClient.getSpendableBalanceDenom(bal.address, unit)?.amount
                ?: BigDecimal.ZERO.toString()
            AssetHolder(
                bal.address,
                CountStrTotal(bal.balance.amount, supply, unit),
                CoinStr(spendableAmount, unit)
            )
        }.toList().sortedWith(compareBy { it.balance.count.toBigDecimal() }).asReversed()
        PagedResults(res.pagination.total.pageCountOfResults(count), list, res.pagination.total)
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

    private fun calculateTokenDistributionStats() {
        val nhashAddr = nhashMarkerAddr()
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
            val results = TokenDistributionPaginatedResultsRecord
                .findRichAccountsByLimitOffset(nhashAddr = nhashAddr, limit = limit, offset = offset)
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
    fun burnedSupply(height: Int? = null) =
        runBlocking { accountClient.getMarkerBalance(nhashMarkerAddr(), UTILITY_TOKEN, height).toBigDecimal().roundWhole() }

    fun moduleAccounts() = AccountRecord.findAccountsByType(listOf(Auth.ModuleAccount::class.java.simpleName))
    fun zeroSeqAccounts() = AccountRecord.findZeroSequenceAccounts()
    fun vestingAccounts() = AccountRecord.findAccountsByType(vestingAccountTypes)
    fun contractAccounts() = AccountRecord.findContractAccounts()
    fun allAccounts() = transaction { AccountRecord.all().toMutableList() }

    fun communityPoolSupply(height: Int? = null) =
        runBlocking {
            accountClient
                .getCommunityPoolAmount(UTILITY_TOKEN, height)
                .toBigDecimal()
                .roundWhole()
        }

    fun richListAccounts() =
        allAccounts().addressList() - zeroSeqAccounts().toSet() - moduleAccounts().addressList() - contractAccounts().addressList() - setOf(nhashMarkerAddr())

    fun totalBalanceForList(addresses: Set<String>, height: Int? = null) = runBlocking {
        if (height != null) {
            addresses.map {
                accountClient.getAccountBalanceForDenomAtHeight(
                    it,
                    UTILITY_TOKEN,
                    height
                )
            }.map {
                CoinStr(it.amount, it.denom)
            }.map {
                it.amount.toBigDecimal()
            }.sumOf { it }
        } else {
            TokenDistributionPaginatedResultsRecord.findByAddresses(addresses).asFlow()
                .map { it.data.count.toBigDecimal() }
                .toList()
                .sumOf { it }
        }
    }

    fun totalSpendableBalanceForList(addresses: Set<String>, height: Int? = null) = runBlocking {
        addresses.asFlow()
            .map {
                accountClient.getSpendableBalanceDenom(it, UTILITY_TOKEN, height)!!.amount.toBigDecimal()
            }
            .toList()
            .sumOf { it }
    }

    // non-spendable = total - spendable
    fun totalNonspendableBalanceForList(addresses: Set<String>, height: Int? = null) = transaction {
        val total = totalBalanceForList(addresses, height)
        val spendable = totalSpendableBalanceForList(addresses, height)
        total - spendable
    }

    // total amount unvested = original amount - vested amount
    fun vestingAccountsUnvestedSupply(atDateTime: LocalDateTime? = null) = vestingAccounts().filter {
        it.data != null && it.data?.isVesting() == true
    }.map {
        // using PeriodInSeconds.YEAR since it will be more performant and doesn't impact data used for calc
        val vestingData = it.data!!.toVestingData(PeriodInSeconds.YEAR, atDateTime)
        val originalAmount = vestingData.originalVestingList.find { it.denom == UTILITY_TOKEN }?.amount?.toBigDecimal()
            ?: BigDecimal.ZERO
        val vestedAmount = vestingData.currentlyVested.find { it.denom == UTILITY_TOKEN }?.amount?.toBigDecimal()
            ?: BigDecimal.ZERO

        originalAmount.minus(vestedAmount)
    }.sumOf { it }

    // max supply = supply from bank module
    fun maxSupply(height: Int? = null) = runBlocking { accountClient.getCurrentSupply(UTILITY_TOKEN, height).amount.toBigDecimal() }

    fun maxSupply(denom: String, height: Int? = null) = runBlocking { accountClient.getCurrentSupply(denom, height).amount.toBigDecimal() }

    // total supply = max - burned -> comes from the nhash marker address
    fun totalSupply(height: Int? = null) = maxSupply() - burnedSupply(height).roundWhole()

    // circulating supply = total supply - unvested supply held in vesting accounts
    fun circulatingSupply(height: Int? = null, atDateTime: LocalDateTime? = null): BigDecimal {
        return totalSupply(height)
            .minus(vestingAccountsUnvestedSupply(atDateTime))
            .roundWhole()
    }

    // rich list = all accounts - nhash marker - zero seq - modules - contracts ->>>>>>>>> out of total
    fun richList(topCount: Int = 100, spendable: Boolean = false) = transaction {
        val richListAccounts = TokenDistributionPaginatedResultsRecord
            .findRichAccountsByLimitOffset(nhashAddr = nhashMarkerAddr(), spendable = spendable)

        val tokenTotal = when (spendable) {
            true -> richListAccounts.sumOf {
                    it.spendable.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                }
            else -> totalSupply()
        }

        richListAccounts.subList(0, topCount).map {
            val (amount, denom) = (it.spendable.amount to it.spendable.denom).takeIf { spendable }
                ?: (it.data.count to it.data.denom)
            RichAccount(
                it.ownerAddress,
                CoinStr(amount, denom),
                amount.toPercentage(BigDecimal(100), tokenTotal, 4)
            )
        }
    }

    fun getTokenHistorical(fromDate: LocalDateTime?, toDate: LocalDateTime?) =
        TokenHistoricalDailyRecord.findForDates(fromDate?.startOfDay(), toDate?.startOfDay())

    fun getTokenLatest() = CacheUpdateRecord.fetchCacheByKey(CacheKeys.UTILITY_TOKEN_LATEST.key)?.cacheValue?.let {
        objectMapper.readValue<CmcLatestDataAbbrev>(it)
    }

    fun fetchHistoricalPriceData(fromDate: LocalDateTime?): List<HistoricalPrice> = runBlocking {
        val allPrices = historicalPriceFetchers.flatMap { fetcher ->
            fetcher.fetchHistoricalPrice(fromDate)
        }
        return@runBlocking allPrices
    }

    fun processHistoricalData(startDate: LocalDateTime, today: LocalDateTime, historicalPrices: List<HistoricalPrice>): List<CmcHistoricalQuote> {
        // TODO - this should be using LocalDate as a function input to avoid issues where the LocalDateTime
        // is not start of day
        val baseMap = generateSequence(startDate) {
            it.plusDays(1)
        }.takeWhile {
            it.isBefore(today)
        }.map {
            it to emptyList<HistoricalPrice>()
        }.toMap().toMutableMap()

        var prevPrice = TokenHistoricalDailyRecord.lastKnownPriceForDate(startDate)

        baseMap.putAll(
            historicalPrices
                .filter { LocalDateTime.ofInstant(Instant.ofEpochSecond(it.time), ZoneOffset.UTC).startOfDay() != today }
                .groupBy { LocalDateTime.ofInstant(Instant.ofEpochSecond(it.time), ZoneOffset.UTC).startOfDay() }
        )

        return baseMap.map { (k, v) ->
            val high = v.maxByOrNull { it.high.toThirdDecimal() }
            val low = v.minByOrNull { it.low.toThirdDecimal() }
            val open = v.minByOrNull { Instant.ofEpochSecond(it.time) }?.open ?: prevPrice
            val close = v.maxByOrNull { Instant.ofEpochSecond(it.time) }?.close ?: prevPrice
            val closeDate = k.plusDays(1).minusNanos(1000000)
            val usdVolume = v.sumOf { it.volume.toThirdDecimal() }.stripTrailingZeros()
            CmcHistoricalQuote(
                time_open = k,
                time_close = closeDate,
                time_high = if (high != null) LocalDateTime.ofInstant(Instant.ofEpochSecond(high.time), ZoneOffset.UTC) else k,
                time_low = if (low != null) LocalDateTime.ofInstant(Instant.ofEpochSecond(low.time), ZoneOffset.UTC) else k,
                quote = mapOf(
                    USD_UPPER to
                        CmcQuote(
                            open = open,
                            high = high?.high ?: prevPrice,
                            low = low?.low ?: prevPrice,
                            close = close,
                            volume = usdVolume,
                            market_cap = close.multiply(
                                totalSupply().divide(UTILITY_TOKEN_BASE_MULTIPLIER)
                            ).toThirdDecimal(),
                            timestamp = closeDate
                        )
                )
            ).also { prevPrice = close }
        }
    }

    fun updateAndSaveTokenHistoricalData(startDate: LocalDateTime, endDate: LocalDateTime) {
        val historicalPrices = fetchHistoricalPriceData(startDate)
        val processedData = processHistoricalData(startDate, endDate, historicalPrices)
        processedData.forEach { record ->
            val source = historicalPriceFetchers.joinToString(separator = ",") { it.getSource() }
            TokenHistoricalDailyRecord.save(record.time_open.startOfDay(), record, source)
        }
    }

    fun updateAndSaveLatestTokenData(startDate: LocalDateTime, today: LocalDateTime) {
        fetchHistoricalPriceData(startDate).sortedBy { it.time }.let {
            processLatestTokenData(it, today)?.let { data ->
                cacheLatestTokenData(data)
            }
        }
    }

    fun processLatestTokenData(list: List<HistoricalPrice>, today: LocalDateTime): CmcLatestDataAbbrev? {
        val prevRecord = list.firstOrNull() ?: return null
        val price = list.last().close.toThirdDecimal()
        val percentChg = prevRecord.priceChangePercentage24h?.takeIf { list.size == 1 }
            ?: price.percentChange(prevRecord.close.toThirdDecimal())
        val vol24Hr = list.sumOf { it.volume.toThirdDecimal() }.stripTrailingZeros()
        val marketCap = price.multiply(totalSupply().divide(UTILITY_TOKEN_BASE_MULTIPLIER)).toThirdDecimal()

        return CmcLatestDataAbbrev(
            today,
            mapOf(USD_UPPER to CmcLatestQuoteAbbrev(price, percentChg, vol24Hr, marketCap, today))
        )
    }

    protected fun cacheLatestTokenData(data: CmcLatestDataAbbrev) {
        CacheUpdateRecord.updateCacheByKey(
            CacheKeys.UTILITY_TOKEN_LATEST.key,
            objectMapper.writeValueAsString(data)
        )
    }

    fun getHashPricingDataDownload(filters: TokenHistoricalDataRequest, resp: ServletOutputStream): ZipOutputStream {
        validate(filters.datesValidation())
        val baseFileName = filters.getFileNameBase()

        val fileList = runBlocking {
            val data = fetchHistoricalPriceData(filters.fromDate)
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
}

fun BigDecimal.asPercentOf(divisor: BigDecimal): BigDecimal = this.divide(divisor, 20, RoundingMode.CEILING)
