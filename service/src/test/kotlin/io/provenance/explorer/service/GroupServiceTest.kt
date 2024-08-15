package io.provenance.explorer.service

import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.config.RestConfig
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.TxUpdate
import io.provenance.explorer.grpc.v1.GroupGrpcClient
import org.flywaydb.core.Flyway
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

class GroupServiceTest {
    private lateinit var restConfig : RestConfig
    private lateinit var groupService: GroupService
    private lateinit var groupClient: GroupGrpcClient
    private lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
        var testnetUri = "grpcs://grpc.test.provenance.io:443"
        // var mainnetUri = "grpcs://grpc.provenance.io:443"
        val uri = URI(testnetUri)
        groupClient = GroupGrpcClient(uri)
        accountService = mock(AccountService::class.java)
        groupService = GroupService(groupClient, accountService)
        restConfig = RestConfig()
    }

    @Test
    @Disabled("This test is used for manually executing group txs from a file.")
    fun testSaveGroups() {
        // This test was written to fix problems with saving groups.
        // We had no easy way to test without trying to run the whole application.
        // This is a way to partially simulate what is going on with a tx with groups.
        // It reads a specific file from the resource directory and then calls saveGroups.
        // From there, you can debug what is going on.
        // To get specific tx to run, you can create a file by calling the grpc endpoint:
        // grpcurl -d "{\"hash\":\"$hash\"}" grpc.provenance.io:443 cosmos.tx.v1beta1.Service.GetTx for mainnet
        // and grpcurl -d "{\"hash\":\"$hash\"}" grpc.test.provenance.io:443 cosmos.tx.v1beta1.Service.GetTx for testnet.
        // We will want to provide some legitimate tests in the future for all new functions.

        Database.connect("jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        transaction {
            exec("""
        CREATE TABLE IF NOT EXISTS process_queue (
            id SERIAL PRIMARY KEY,
            process_type VARCHAR(128) NOT NULL,
            process_value TEXT NOT NULL,
            processing BOOLEAN NOT NULL DEFAULT FALSE
        );
    """)
        }

        val jsonFilePath = Paths.get("src/test/resources/group/group-testnet-417C18D1FAF6B18ECFF55F81CED5038FF8DD739CA6C5C29C8901AB0510426F65.json")
        val jsonResponse = String(Files.readAllBytes(jsonFilePath))
        val txResponseBuilder = ServiceOuterClass.GetTxResponse.newBuilder()
        restConfig.protoParser()!!.merge(jsonResponse, txResponseBuilder)
        val txResponse = txResponseBuilder.build()

        val txData = TxData( txResponse.txResponse.height.toInt(), 0, txResponse.txResponse.txhash, DateTime())
        val txUpdate = TxUpdate(txResponse.txResponse.txhash,)

        groupService.saveGroups(txResponse, txData, txUpdate)
    }

}