package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.tx
import io.provenance.explorer.config.RestConfig
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.TxUpdate
import io.provenance.explorer.grpc.v1.GroupGrpcClient
import org.jetbrains.exposed.sql.Database
import org.joda.time.DateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.nio.file.Files
import java.nio.file.Paths

class GroupServiceTest {
    private lateinit var restConfig : RestConfig
    private lateinit var groupService: GroupService
    private lateinit var groupClient: GroupGrpcClient
    private lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
        groupClient = mock(GroupGrpcClient::class.java)
        accountService = mock(AccountService::class.java)
        groupService = GroupService(groupClient, accountService)
        restConfig = RestConfig()
    }

    @Test
    @Disabled("need to finish")
    fun testSaveGroups() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")

        val jsonFilePath = Paths.get("src/test/resources/group/group-testnet-9CBD6E9465A4747043B7665338F00B71B6F6371AACA6388715B730978BB1EE6C.json")
        val jsonResponse = String(Files.readAllBytes(jsonFilePath))
        val txResponseBuilder = ServiceOuterClass.GetTxResponse.newBuilder()
        restConfig.protoParser()!!.merge(jsonResponse, txResponseBuilder)
        val txResponse = txResponseBuilder.build()

        val txData = TxData( 18232283, 0, txResponse.txResponse.txhash, DateTime())
        val txUpdate = TxUpdate(txResponse.txResponse.txhash,)

        groupService.saveGroups(txResponse, txData, txUpdate)
    }

}