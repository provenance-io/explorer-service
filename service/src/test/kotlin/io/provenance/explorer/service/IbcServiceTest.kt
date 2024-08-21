package io.provenance.explorer.service

import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.config.RestConfig
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.TxUpdate
import io.provenance.explorer.grpc.v1.GroupGrpcClient
import io.provenance.explorer.grpc.v1.IbcGrpcClient
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths

class IbcServiceTest {
    private lateinit var restConfig: RestConfig
    private lateinit var ibcGrpcClient: IbcGrpcClient
    private lateinit var assetService: AssetService
    private lateinit var ibcService: IbcService
    private lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
//        var testnetUri = "grpcs://grpc.test.provenance.io:443"
        var mainnetUri = "grpcs://grpc.provenance.io:443"
        val uri = URI(mainnetUri)
        ibcGrpcClient = IbcGrpcClient(uri)
        accountService = mock(AccountService::class.java)
        assetService = mock(AssetService::class.java)
        restConfig = RestConfig()
        ibcService = IbcService(ibcGrpcClient, assetService, accountService, restConfig.protoPrinter()!!)
    }

    @Test
    @Disabled("This test is used for manually executing group txs from a file.")
    fun testIbc() {
        Database.connect("jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        transaction {
            exec(
                """
    """
            )
        }

        val jsonFilePath =
            Paths.get("src/test/resources/ibc/ibc-recv-parse.json")
        val jsonResponse = String(Files.readAllBytes(jsonFilePath))
        val txResponseBuilder = ServiceOuterClass.GetTxResponse.newBuilder()
        restConfig.protoParser()!!.merge(jsonResponse, txResponseBuilder)
        val txResponse = txResponseBuilder.build()

        val txData = TxData(txResponse.txResponse.height.toInt(), 0, txResponse.txResponse.txhash, DateTime())
        val txUpdate = TxUpdate(txResponse.txResponse.txhash)
        ibcService.saveIbcChannelData(txResponse, txData, txUpdate)
    }
}
