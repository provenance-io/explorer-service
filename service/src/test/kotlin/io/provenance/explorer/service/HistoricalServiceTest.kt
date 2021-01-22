package io.provenance.explorer.service

import com.fasterxml.jackson.core.type.TypeReference
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.models.clients.tendermint.JsonRpc
import io.provenance.explorer.domain.models.clients.tendermint.TendermintBlockchainResponse
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner


@RunWith(SpringRunner::class)
class HistoricalServiceTest {


    @MockBean
    lateinit var transactionService: TransactionService

    @MockBean
    lateinit var blockService: BlockService

    @MockBean
    lateinit var cacheService: CacheService

    @Test
    fun `should create a new block index and start collecting 2 days of historical blocks`() {
        `when`(cacheService.getBlockIndex()).thenReturn(null)
        `when`(blockService.getLatestBlockHeight()).thenReturn(80)
        `when`(blockService.getBlockchain(80)).thenReturn(getBlockchainResponse("tm-blockchain-80.json"))
        `when`(blockService.getBlockchain(60)).thenReturn(getBlockchainResponse("tm-blockchain-60.json"))

        val explorerProperties = ExplorerProperties()
        explorerProperties.initialHistoricalDayCount = "2"
        val historicalService = object : HistoricalService(explorerProperties, cacheService, blockService, transactionService) {
            override fun getEndDate(): DateTime {
                return LocalDate.parse("2020-05-10").toDateTimeAtStartOfDay()
            }
        }

        historicalService.updateCache()

        verify(cacheService, times(38)).addBlockToCache(anyInt(), anyInt(), anyObject(), anyObject())
        verify(transactionService, times(1)).addTransactionsToCache(61, 3)
        verify(cacheService, times(1)).updateBlockMinHeightIndex(61)
        verify(cacheService, times(1)).updateBlockMinHeightIndex(43)
    }

    @Test
    fun `should complete collecting 3 days of historical blocks from incomplete historical set`() {
        `when`(blockService.getLatestBlockHeight()).thenReturn(80)
        `when`(blockService.getBlockchain(60)).thenReturn(getBlockchainResponse("tm-blockchain-60.json"))

        val explorerProperties = ExplorerProperties()
        explorerProperties.initialHistoricalDayCount = "2"
        val historicalService = object : HistoricalService(explorerProperties, cacheService, blockService, transactionService) {
            override fun getBlockIndex(): Pair<Int, Int>? {
                return Pair<Int, Int>(80, 61)
            }

            override fun getEndDate(): DateTime {
                return LocalDate.parse("2020-05-10").toDateTimeAtStartOfDay()
            }
        }
        historicalService.updateCache()

        verify(cacheService, times(18)).addBlockToCache(anyInt(), anyInt(), anyObject(), anyObject())
        verify(cacheService, times(1)).updateBlockMinHeightIndex(43)
    }

    @Test
    fun `should collect from the most recent height to the max index`() {
        `when`(blockService.getLatestBlockHeight()).thenReturn(80)
        `when`(blockService.getBlockchain(80)).thenReturn(getBlockchainResponse("tm-blockchain-80.json"))

        val explorerProperties = ExplorerProperties()
        explorerProperties.initialHistoricalDayCount = "2"
        val historicalService = object : HistoricalService(explorerProperties, cacheService, blockService, transactionService) {
            override fun getBlockIndex(): Pair<Int, Int>? {
                return Pair<Int, Int>(70, 20)
            }

            override fun continueCollectingHistoricalBlocks(maxRead: Int, minRead: Int): Boolean {
                return false
            }
        }

        historicalService.updateCache()

        verify(cacheService, times(10)).addBlockToCache(anyInt(), anyInt(), anyObject(), anyObject())
        verify(cacheService, times(1)).updateBlockMaxHeightIndex(80)
    }

    fun getBlockchainResponse(jsonFileName: String) = OBJECT_MAPPER.readValue(javaClass.classLoader.getResourceAsStream("json/${this.javaClass.simpleName}/$jsonFileName"), object : TypeReference<JsonRpc<TendermintBlockchainResponse>>() {})

    fun <T> anyObject(): T {
        any<T>()
        return uninitialized()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> uninitialized(): T = null as T

}
