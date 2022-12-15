package io.provenance.explorer.client

import com.fasterxml.jackson.databind.ObjectMapper
import feign.Feign
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder

object BaseRoutes {
    const val V2_BASE = "/api/v2"
    const val V3_BASE = "/api/v3"
}

interface BaseClient {
    companion object {
        const val CT_JSON = "Content-Type: application/json"
    }
}

class ExplorerClient(url: String) {
    companion object {
        private fun builder(
            withMapper: ObjectMapper = ObjectMapper(),
            withBuilder: Feign.Builder =
                Feign.builder()
                    .encoder(JacksonEncoder(withMapper))
                    .decoder(JacksonDecoder(withMapper))
                    .dismiss404()
        ) = withBuilder
    }

    val accountClient = builder().target(AccountClient::class.java, url)
    val assetClient = builder().target(AssetClient::class.java, url)
    val blockClient = builder().target(BlockClient::class.java, url)
    val generalClient = builder().target(GeneralClient::class.java, url)
    val governanceClient = builder().target(GovernanceClient::class.java, url)
    val grantsClient = builder().target(GrantsClient::class.java, url)
    val ibcClient = builder().target(IbcClient::class.java, url)
    val nameClient = builder().target(NameClient::class.java, url)
    val nftClient = builder().target(NftClient::class.java, url)
    val smartContractClient = builder().target(SmartContractClient::class.java, url)
    val tokenClient = builder().target(TokenClient::class.java, url)
    val transactionClient = builder().target(TransactionClient::class.java, url)
    val validatorClient = builder().target(ValidatorClient::class.java, url)
}
