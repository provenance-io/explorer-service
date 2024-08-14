package io.provenance.explorer.grpc.extensions

import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.config.RestConfig
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals

class HelperTest {

    var restConfig = RestConfig()

    fun getTxResponse(fileName: String): ServiceOuterClass.GetTxResponse {
        val jsonFilePath = Paths.get("src/test/resources/grpc/extensions/$fileName")
            val jsonResponse = String(Files.readAllBytes(jsonFilePath))
            val txResponseBuilder = ServiceOuterClass.GetTxResponse.newBuilder()
            val parser = restConfig.protoParser()

            parser?.merge(jsonResponse, txResponseBuilder)
            return txResponseBuilder.build()
    }

    @Test
    fun `test map event attr values from logs and msg index`() {
        val response = getTxResponse("submit-proposal-with-logs-and-msg-index.json")
        var proposalId = response.mapEventAttrValuesFromLogs(
            1,
            GroupProposalEvents.GROUP_SUBMIT_PROPOSAL.event,
            GroupProposalEvents.GROUP_SUBMIT_PROPOSAL.idField.toList()
        )[GroupProposalEvents.GROUP_SUBMIT_PROPOSAL.idField.first()]!!.toLong()
        assertEquals(111500, proposalId)

         proposalId = response.mapEventAttrValuesByMsgIndex(1,
            GroupProposalEvents.GROUP_SUBMIT_PROPOSAL.event,
            GroupProposalEvents.GROUP_SUBMIT_PROPOSAL.idField.toList()
        )[GroupProposalEvents.GROUP_SUBMIT_PROPOSAL.idField.first()]!!.toLong()
        assertEquals(111500, proposalId)

    }
}