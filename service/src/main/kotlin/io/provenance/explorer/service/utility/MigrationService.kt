package io.provenance.explorer.service.utility

import io.ktor.client.features.ResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.provenance.explorer.KTOR_CLIENT_JAVA
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.sql.batchInsert
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockCacheTable
import io.provenance.explorer.domain.entities.TokenHistoricalDailyRecord
import io.provenance.explorer.domain.entities.TokenHistoricalDailyTable
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.models.explorer.CmcHistoricalQuote
import io.provenance.explorer.domain.models.explorer.CmcQuote
import io.provenance.explorer.service.AccountService
import io.provenance.explorer.service.BlockService
import io.provenance.explorer.service.ValidatorService
import io.provenance.explorer.service.async.AsyncCachingV2
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.Interval
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class MigrationService(
    private val asyncCaching: AsyncCachingV2,
    private val validatorService: ValidatorService,
    private val accountService: AccountService,
    private val blockService: BlockService
) {

    protected val logger = logger(MigrationService::class)

    fun updateAccounts(list: List<String>) = transaction {
        list.forEach { accountService.saveAccount(it) }
    }

    fun updateBlocks(startHeight: Int, endHeight: Int, inc: Int) {
        logger.info("Start height: $startHeight")
        var start = startHeight
        while (start <= endHeight) {
            transaction {
                logger.info("Fetching $start to ${start + inc - 1}")
                BlockCacheRecord.find { BlockCacheTable.id.between(start, start + inc - 1) }
                    .orderBy(Pair(BlockCacheTable.id, SortOrder.ASC)).forEach {
                        asyncCaching.saveBlockEtc(it.block, Pair(true, false))
                    }
            }
            start += inc
        }
        logger.info("End height: $endHeight")
    }

    fun insertBlocks(blocks: List<Int>, pullFromDb: Boolean) = transaction {
        blocks.forEach { block ->
            blockService.getBlockAtHeightFromChain(block)?.let {
                asyncCaching.saveBlockEtc(it, Pair(true, pullFromDb))
            }
        }
    }

    fun updateMissedBlocks(startHeight: Int, endHeight: Int, inc: Int) {
        logger.info("Start height: $startHeight")
        var start = startHeight
        while (start <= endHeight) {
            transaction {
                logger.info("Fetching $start to ${start + inc - 1}")
                BlockCacheRecord.find { BlockCacheTable.id.between(start, start + inc - 1) }
                    .orderBy(Pair(BlockCacheTable.id, SortOrder.ASC)).forEach {
                        validatorService.saveMissedBlocks(it.block)
                    }
            }
            start += inc
        }
        logger.info("End height: $endHeight")
    }


    private fun fromDlob(): DlobHistBase? = runBlocking {
        try {
            KTOR_CLIENT_JAVA.get("https://www.dlob.io:443/gecko/external/api/v1/exchange/historical_trades") {
                parameter("ticker_id", "HASH_USD")
                parameter("type", "buy")
                parameter("start_time", "18-05-2021")
                parameter("end_time", "09-10-2021")
                accept(ContentType.Application.Json)
            }
        } catch (e: ResponseException) {
            return@runBlocking null
                .also { logger.error("Error fetching from Dlob: ${e.response}") }
        }
    }

    fun getFromDlob() = transaction {
        var openInit: BigDecimal? = null
        fromDlob()?.buy?.groupBy { DateTime(it.trade_timestamp * 1000).startOfDay() }
            ?.toSortedMap()
            ?.map { (k,v) ->
                val high = v.maxByOrNull { it.price }!!
                val low = v.minByOrNull { it.price }!!
                val open = v.minByOrNull { DateTime(it.trade_timestamp * 1000) }!!.price
                val close = v.maxByOrNull { DateTime(it.trade_timestamp * 1000) }!!.price
                val closeDate = k.plusDays(1).minusMillis(1)
                val usdVolume = v.sumOf { it.target_volume }.stripTrailingZeros()
                CmcHistoricalQuote(
                    time_open = k,
                    time_close = closeDate,
                    time_high = DateTime(high.trade_timestamp * 1000),
                    time_low = DateTime(low.trade_timestamp * 1000),
                    quote = mapOf("USD" to
                        CmcQuote(
                            open = openInit ?: open,
                            high = high.price,
                            low = low.price,
                            close = close,
                            volume = usdVolume,
                            market_cap = BigDecimal.ZERO,
                            timestamp = closeDate
                        )
                    )
                ).also { openInit = close }
            }?.let { list ->
                val timestamp = TokenHistoricalDailyTable.timestamp
                val data = TokenHistoricalDailyTable.data
                TokenHistoricalDailyTable
                    .batchInsert(list, listOf(timestamp)) { batch, quote ->
                        batch[timestamp] = quote.time_open.startOfDay()
                        batch[data] = quote
                    }
            }
    }

    fun populateBack() = transaction {
        TokenHistoricalDailyRecord.all().orderBy(Pair(TokenHistoricalDailyTable.timestamp, SortOrder.ASC)).first()
            .let { record ->
                val startDate = DateTime(2021, 4, 20, 0, 0)
                Interval(startDate, record.timestamp)
                    .let { int -> generateSequence(int.start) {dt -> dt.plusDays(1) }.takeWhile { dt -> dt < int.end } }
                    .map {
                        val dt = it.startOfDay()
                        val timeClose = dt.plusDays(1).minusMillis(1)
                        val quoteCopy = record.data.quote["USD"]!!.copy(timestamp = timeClose)
                        record.data.copy(
                            time_open = dt,
                            time_close = timeClose,
                            time_high = dt,
                            time_low = dt,
                            quote = mapOf("USD" to quoteCopy)
                        )
                    }.toList().let { list ->
                        val timestamp = TokenHistoricalDailyTable.timestamp
                        val data = TokenHistoricalDailyTable.data
                        TokenHistoricalDailyTable
                            .batchInsert(list, listOf(timestamp)) { batch, quote ->
                                batch[timestamp] = quote.time_open.startOfDay()
                                batch[data] = quote
                            }
                    }


            }
    }
}

data class DlobAggregate(
    val date: DateTime,
    val price: BigDecimal,
    val qty: Int,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val usdVolume: BigDecimal
)

data class DlobHistBase(
    val buy: List<DlobHistorical>
)

data class DlobHistorical(
    val trade_id: Long,
    val price: BigDecimal,
    val base_volume: Long,
    val target_volume: BigDecimal,
    val trade_timestamp: Long,
    val type: String
)
