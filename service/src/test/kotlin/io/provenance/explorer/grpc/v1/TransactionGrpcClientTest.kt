package io.provenance.explorer.grpc.v1

import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.domain.exceptions.TendermintApiException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.net.URI

class TransactionGrpcClientTest {

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test getTxsByHeight success`() = runBlocking {
        val uri = URI("grpcs://grpc.test.provenance.io:443")
        val transactionGrpcClient = TransactionGrpcClient(uri)

        try {
            val height = 23440770
            val total = 1

            val txResponses: List<ServiceOuterClass.GetTxResponse> = transactionGrpcClient.getTxsByHeight(height, total)

            txResponses.forEach { txResponse ->
                println("TxResponse: ${txResponse.txResponse}")
                println("Tx: ${txResponse.tx}")
            }
        } catch (e: TendermintApiException) {
            println("Error occurred: ${e.message}")
        }
    }
}
