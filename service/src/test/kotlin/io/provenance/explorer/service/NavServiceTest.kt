package io.provenance.explorer.service

import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.config.RestConfig
import io.provenance.explorer.domain.entities.NavEventsRecord
import io.provenance.explorer.domain.entities.NavEventsTable
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.TxUpdate
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class NavServiceTest {
    private lateinit var restConfig: RestConfig
    private lateinit var navService: NavService

    companion object {
        init {
            Database.connect("jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
            transaction {
                var sql = this::class.java.getResource("/db/migration/V1_96__Add_nav_event_table.sql")!!.readText()
                sql = sql.replace("TIMESTAMPTZ", "TIMESTAMP").replace("TEXT", "VARCHAR(255)")
                exec(sql)
            }
        }
    }

    @BeforeEach
    fun setUp() {
        restConfig = RestConfig()
        navService = NavService()
    }

    @AfterEach
    fun cleanup() {
        transaction {
            NavEventsTable.deleteAll()
        }
    }

    @Test
    fun `test tx response with set nav for markers`() {
        val jsonFilePath = Paths.get("src/test/resources/navs/nav-mainnet-marker-C4EF515FCA62F01569E6E8E4DEB4E24A717EBEBEB240BEA7CE346356394ECC2D.json")
        val jsonResponse = String(Files.readAllBytes(jsonFilePath))
        val txResponseBuilder = ServiceOuterClass.GetTxResponse.newBuilder()
        restConfig.protoParser()!!.merge(jsonResponse, txResponseBuilder)
        val txResponse = txResponseBuilder.build()

        val txData = TxData(
            txResponse.txResponse.height.toInt(),
            0,
            txResponse.txResponse.txhash,
            DateTime()
        )
        val txUpdate = TxUpdate(txResponse.txResponse.txhash)

        navService.saveNavs(txResponse, txData, txUpdate)

        transaction {
            val allEvents = NavEventsRecord.all().toList()
            assertEquals(2, allEvents.size, "Should have saved exactly 2 NAV events from the transaction")

            val ethEvent = allEvents.find { it.denom == "neth.figure.se" }
            assertNotNull(ethEvent, "ETH NAV event should be present in saved records")
            ethEvent?.let {
                assertEquals("2513160000", it.priceAmount.toString(), "ETH price amount should match transaction value")
                assertEquals("uusd.trading", it.priceDenom, "ETH price denomination should be uusd.trading")
                assertEquals("1000000000", it.volume.toString(), "ETH volume should match transaction value")
                assertEquals("x/exchange market 1", it.source, "ETH event source should be exchange market 1")
                assertEquals(txResponse.txResponse.height.toInt(), it.blockHeight, "ETH event block height should match transaction")
                assertEquals(txResponse.txResponse.txhash, it.txHash, "ETH event transaction hash should match")
            }

            val btcEvent = allEvents.find { it.denom == "nbtc.figure.se" }
            assertNotNull(btcEvent, "BTC NAV event should be present in saved records")
            btcEvent?.let {
                assertEquals("1688067490", it.priceAmount.toString(), "BTC price amount should match transaction value")
                assertEquals("uusd.trading", it.priceDenom, "BTC price denomination should be uusd.trading")
                assertEquals("25400000", it.volume.toString(), "BTC volume should match transaction value")
                assertEquals("x/exchange market 1", it.source, "BTC event source should be exchange market 1")
                assertEquals(txResponse.txResponse.height.toInt(), it.blockHeight, "BTC event block height should match transaction")
                assertEquals(txResponse.txResponse.txhash, it.txHash, "BTC event transaction hash should match")
            }
        }
    }

    @Test
    fun `test tx response with set nav for scopes`() {
        val jsonFilePath = Paths.get("src/test/resources/navs/nav-mainnet-scope-8B13B35A4780884E3AA91C015B1574AA59BE9237D51D0D1ADABF0AE5DEC7B666.json")
        val jsonResponse = String(Files.readAllBytes(jsonFilePath))
        val txResponseBuilder = ServiceOuterClass.GetTxResponse.newBuilder()
        restConfig.protoParser()!!.merge(jsonResponse, txResponseBuilder)
        val txResponse = txResponseBuilder.build()

        val txData = TxData(
            txResponse.txResponse.height.toInt(),
            0,
            txResponse.txResponse.txhash,
            DateTime()
        )
        val txUpdate = TxUpdate(txResponse.txResponse.txhash)

        navService.saveNavs(txResponse, txData, txUpdate)

        transaction {
            val allEvents = NavEventsRecord.all().toList()
            assertEquals(2, allEvents.size, "Should have saved exactly 2 NAV events from the transaction")

            val firstScope = allEvents.find { it.scopeId == "scope1qr0d9zmyj4ty295hkkwyq8glyneqdhwf2q" }
            assertNotNull(firstScope, "First scope NAV event should be present in saved records")
            firstScope?.let {
                assertEquals("55497490", it.priceAmount.toString(), "First scope price amount should match transaction value")
                assertEquals("usd", it.priceDenom, "First scope price denomination should be usd")
                assertEquals("1", it.volume.toString(), "First scope volume should be 1")
                assertEquals("metadata", it.source, "First scope event source should be metadata")
                assertEquals(txResponse.txResponse.height.toInt(), it.blockHeight, "First scope event block height should match transaction")
                assertEquals(txResponse.txResponse.txhash, it.txHash, "First scope event transaction hash should match")
            }

            val secondScope = allEvents.find { it.scopeId == "scope1qzsj2jjl4cdyq2ugpgaj2d0grjks26j5ys" }
            assertNotNull(secondScope, "Second scope NAV event should be present in saved records")
            secondScope?.let {
                assertEquals("80545130", it.priceAmount.toString(), "Second scope price amount should match transaction value")
                assertEquals("usd", it.priceDenom, "Second scope price denomination should be usd")
                assertEquals("1", it.volume.toString(), "Second scope volume should be 1")
                assertEquals("metadata", it.source, "Second scope event source should be metadata")
                assertEquals(txResponse.txResponse.height.toInt(), it.blockHeight, "Second scope event block height should match transaction")
                assertEquals(txResponse.txResponse.txhash, it.txHash, "Second scope event transaction hash should match")
            }
        }
    }
}
