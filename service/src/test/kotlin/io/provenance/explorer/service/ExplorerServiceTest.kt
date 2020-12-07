//package io.provenance.explorer.service
//
//import com.fasterxml.jackson.databind.JsonNode
//import com.fasterxml.jackson.databind.ObjectMapper
//import io.provenance.explorer.Application
//import io.provenance.explorer.OBJECT_MAPPER
//import io.provenance.explorer.client.PbClient
//import io.provenance.explorer.domain.PbStakingValidator
//import io.provenance.explorer.domain.RecentTx
//import io.provenance.pbc.clients.*
//import org.joda.time.DateTime
//import org.junit.Assert
//import org.junit.Ignore
//import org.junit.Test
//import org.junit.runner.RunWith
//import org.springframework.beans.factory.annotation.Autowired
//import org.springframework.boot.test.context.SpringBootTest
//import org.springframework.test.context.ActiveProfiles
//import org.springframework.test.context.junit4.SpringRunner
//import org.springframework.transaction.annotation.Transactional
//import java.net.HttpURLConnection
//import java.net.URL
//import java.math.BigDecimal
//
//
//@RunWith(SpringRunner::class)
//@SpringBootTest(classes = [Application::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@ActiveProfiles("test")
//@Transactional
//open class ExplorerServiceTest {
//
//
//    @Autowired
//    lateinit var explorerService: ExplorerService
//
//    @Autowired
//    lateinit var pbClient: PbClient
//
////    @Mock @Autowired lateinit var restTemplate: RestTemplate
//
//    @Test
////    @Ignore("TODO turn into integration tests")
//    fun `should return recent blocks of different sizes and sort order`() {
//        var result = explorerService.getRecentBlocks(25, -1, "")
//        Assert.assertEquals(25, result.results.size)
//        result = explorerService.getRecentBlocks(102, 0, "asc")
//        Assert.assertEquals(102, result.results.size)
//    }
//
//    @Test
////    @Ignore("TODO turn into integration tests")
//    fun `should return a block at specific height`() {
//        var result = explorerService.getBlockAtHeight(2840284)
//    }
//
//    @Test
////    @Ignore("TODO turn into integration tests")
//    fun `should return most recent transactions`() {
//        val result = mutableListOf<RecentTx>()
//        result.addAll(explorerService.getRecentTransactions(30, 0, "").results)
//        result.addAll(explorerService.getRecentTransactions(30, 1, "").results)
//        Assert.assertEquals(60, result.count())
//    }
//
//
////    3137224
//    @Test
////    @Ignore("TODO turn into integration tests")
//    fun `should return transaction by hash`() {
//        val transferHash = "270384FE0D39104C3376BE96D6D71772EADA5CE21460CE2C889CF8873A5B4596"
//        val nameBoundHash = "AF558EF8E903D76F60EE41D415276FED32A542FBF278EE9218C4619E3DB6F641"
//        val transferTransaction = explorerService.getTransactionByHash(transferHash)
//        val nameBoundTransaction = explorerService.getTransactionByHash(nameBoundHash)
//    }
//
//    @Test
////    @Ignore("TODO turn into integration tests")
//    fun `get status`() {
//        val result = explorerService.getStatus()
//    }
//
//    @Test
////    @Ignore("TODO turn into integration tests")
//    fun `get current height 2827095`() {
//        val currentHeight = explorerService.getLatestBlockHeight()
//    }
//
//    @Test
////    @Ignore("TODO turn into integration tests")
//    fun `test latest height job`() {
//        explorerService.updateLatestBlockHeightJob()
//        explorerService.updateLatestBlockHeightJob()
//    }
//
////    @Test
////    @Ignore("TODO turn into integration tests")
////    fun `test get rest`() {
////        val result = explorerService.getRestTendermintResult("https://test.provenance.io/explorer/sentinel/status")
////        Assert.assertTrue(result is JsonNode)
////    }
//
//    @Test
////    @Ignore("TODO turn into integration tests")
//    fun `get current validators at height and page v1`() {
////        explorerService.getLatestBlockHeight()
//        Thread.sleep(5000)
////        var result = explorerService.getRecentValidatorsV2(11, 0, "asc")
////        Assert.assertEquals(4, result.results.size)
////        result = explorerService.getRecentValidatorsV2(1, 1, "asc")
////        Assert.assertEquals(1, result.results.size)
////        result = explorerService.getRecentValidatorsV2(2, 1, "asc")
////        Assert.assertEquals(2, result.results.size)
//    }
//
//    @Test
////    @Ignore("TODO turn into integration tests")
//    fun `get current validators at height and page v2`() {
////        explorerService.getLatestBlockHeight()
//        Thread.sleep(5000)
//        var result = explorerService.getRecentValidators(11, 0, "asc")
//        Assert.assertEquals(4, result.results.size)
//        result = explorerService.getRecentValidators(1, 1, "asc")
//        Assert.assertEquals(1, result.results.size)
//        result = explorerService.getRecentValidators(2, 1, "asc")
//        Assert.assertEquals(2, result.results.size)
//    }
//
//    @Test
////    @Ignore("TODO turn into integration tests")
//    fun `should get validator by address id of latest block`() {
//        var result = explorerService.getValidator("")
//        Assert.assertNull(result)
//        result = explorerService.getValidator("")
//        Assert.assertNotNull(result)
//    }
//
//    @Test
////    @Ignore("TODO turn into integration tests")
//    fun `test history init`() {
//        explorerService.updateCache()
//    }
//
//    @Test
////    @Ignore("TODO turn into integration tests")
//    fun `should return spotlight object with current block and average block creation time`() {
//        Thread.sleep(5000)
//        val result = explorerService.getSpotlightStatistics()
//        Assert.assertNotNull(result)
//        Assert.assertTrue(result.avgBlockTime > BigDecimal("0.0"))
//    }
//
//    @Test
////    @Ignore
//    fun `test get transaction history`() {
////        https://test.provenance.io/explorer/secured/api/v1/txs/history?toDate=2020-11-24&fromDate=2020-11-11&granulatiry=day
//        explorerService.getTransactionHistory(DateTime.parse("2020-11-24"), DateTime.parse("2020-11-11"), "day")
//    }
//
//    @Test
////    @Ignore
//    fun `test get transaction v2`() {
//        val tx = explorerService.getTransactionByHashV2("36B73EAF8966995F2793805776B47CAF2B42A0CAE5406B2B3B772888B08574A8")
//        Assert.assertNotNull(tx)
//    }
//
//    @Test
//    fun `get staking pool`() {
//        val pool = explorerService.getStakingPool()
//        Assert.assertNotNull(pool)
//    }
//
//    @Test
//    fun `get staking validators`() {
//        val bounded = pbClient.getStakingValidators("bonded",1, 100)
//        val unbounded = pbClient.getStakingValidators("unbonded",1, 100)
//        val unbounding = pbClient.getStakingValidators("unbonding",1, 100)
//
////        val obj = OBJECT_MAPPER.readValue(pool.toString(), PbResponse::class.java)
//        Assert.assertNotNull(bounded)
//        Assert.assertNotNull(unbounded)
//        Assert.assertNotNull(unbounding)
//    }
//
//    @Test
//    fun `get recent validators v2`() {
//        var result = explorerService.getRecentValidatorsV2(10, 1, "desc", "bonded")
//    }
//
//    @Test
//    fun `get slashing info`() {
//        println("hello")
//        val response = pbClient.getSlashingSigningInfo()
//        println(response)
//    }
//}