package io.provenance.explorer.service

import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.config.RestConfig
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.TxUpdate
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class NavServiceTest {
    private lateinit var restConfig: RestConfig
    private lateinit var navService: NavService

    @BeforeEach
    fun setUp() {
        restConfig = RestConfig()
        navService = NavService()
    }

    @Test
    fun testNavSave() {
        Database.connect("jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        transaction {
            var sql = this::class.java.getResource("/db/migration/V1_96__Add_nav_event_table.sql")!!
                .readText()
            sql = sql
                .replace("TIMESTAMPTZ", "TIMESTAMP")
                .replace("TEXT", "VARCHAR(255)")
            exec(sql)
        }


        val jsonFilePath =
            Paths.get("src/test/resources/navs/nav-mainnet-C4EF515FCA62F01569E6E8E4DEB4E24A717EBEBEB240BEA7CE346356394ECC2D.json")
        val jsonResponse = String(Files.readAllBytes(jsonFilePath))
        val txResponseBuilder = ServiceOuterClass.GetTxResponse.newBuilder()
        restConfig.protoParser()!!.merge(jsonResponse, txResponseBuilder)
        val txResponse = txResponseBuilder.build()

        val txData = TxData(txResponse.txResponse.height.toInt(), 0, txResponse.txResponse.txhash, DateTime())
        val txUpdate = TxUpdate(txResponse.txResponse.txhash)
        navService.saveNavs(txResponse, txData, txUpdate)
    }
}
