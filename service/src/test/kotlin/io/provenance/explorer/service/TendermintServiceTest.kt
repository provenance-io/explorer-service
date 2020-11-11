package io.provenance.explorer.service

import com.fasterxml.jackson.databind.JsonNode
import io.provenance.explorer.Application
import io.provenance.explorer.domain.RecentTx
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.transaction.annotation.Transactional


@RunWith(SpringRunner::class)
@SpringBootTest(classes = [Application::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
open class TendermintServiceTest {


    @Autowired
    lateinit var tendermintService: TendermintService


//    @Mock @Autowired lateinit var restTemplate: RestTemplate

    @Test
    @Ignore("TODO turn into integration tests")
    fun `should return recent blocks of different sizes and sort order`() {
        var result = tendermintService.getRecentBlocks(10, 0, "")
        Assert.assertEquals(10, result.size)
        result = tendermintService.getRecentBlocks(102, 0, "asc")
        Assert.assertEquals(102, result.size)
    }

    @Test
    @Ignore("TODO turn into integration tests")
    fun `should return a block at specific height`() {
        var result = tendermintService.getBlockAtHeight(2840284)
    }

    @Test
    @Ignore("TODO turn into integration tests")
    fun `should return most recent transactions` () {
        val result = mutableListOf<RecentTx>()
        result.addAll(tendermintService.getRecentTransactions(30, 0, ""))
        result.addAll(tendermintService.getRecentTransactions(30, 1, ""))
        Assert.assertEquals(60, result.count())
    }


    @Test
    @Ignore("TODO turn into integration tests")
    fun `get status`() {
        val result = tendermintService.getStatus()
        println(result)
    }

    @Test
    @Ignore("TODO turn into integration tests")
    fun `get current height 2827095`() {
        val currentHeight = tendermintService.getLastestBlockHeight()
        println(currentHeight)
    }

    @Test
    @Ignore("TODO turn into integration tests")
    fun `test latest height job`() {
        tendermintService.updateLatestBlockHeight()
        tendermintService.updateLatestBlockHeight()
    }

    @Test
    @Ignore("TODO turn into integration tests")
    fun `test get rest`() {
        val result = tendermintService.getRestResult("https://test.provenance.io/explorer/sentinel/status")
        Assert.assertTrue(result is JsonNode)
    }
}