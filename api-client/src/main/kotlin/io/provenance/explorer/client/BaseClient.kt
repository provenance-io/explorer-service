package io.provenance.explorer.client

import com.fasterxml.jackson.databind.ObjectMapper
import feign.Feign
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder

object BaseRoutes {
    const val V2_BASE = "/api/v2"
    const val V3_BASE = "/api/v3"
    const val PAGE_PARAMETERS = "page={page}&count={count}"
}

interface BaseClient {
    companion object {
        const val CT_JSON = "Content-Type: application/json"
    }
}

class ExplorerClient(
    url: String,
    objectMapper: ObjectMapper = ObjectMapper()
) {
    companion object {
        private fun builder(
            withMapper: ObjectMapper,
            withBuilder: Feign.Builder =
                Feign.builder()
                    .encoder(JacksonEncoder(withMapper))
                    .decoder(JacksonDecoder(withMapper))
                    .dismiss404()
        ) = withBuilder
    }

    val accountClient = builder(objectMapper).target(AccountClient::class.java, url)
    val assetClient = builder(objectMapper).target(AssetClient::class.java, url)
    val blockClient = builder(objectMapper).target(BlockClient::class.java, url)
    val generalClient = builder(objectMapper).target(GeneralClient::class.java, url)
    val governanceClient = builder(objectMapper).target(GovernanceClient::class.java, url)
    val grantsClient = builder(objectMapper).target(GrantsClient::class.java, url)
    val ibcClient = builder(objectMapper).target(IbcClient::class.java, url)
    val nameClient = builder(objectMapper).target(NameClient::class.java, url)
    val nftClient = builder(objectMapper).target(NftClient::class.java, url)
    val smartContractClient = builder(objectMapper).target(SmartContractClient::class.java, url)
    val tokenClient = builder(objectMapper).target(TokenClient::class.java, url)
    val transactionClient = builder(objectMapper).target(TransactionClient::class.java, url)
    val validatorClient = builder(objectMapper).target(ValidatorClient::class.java, url)
}
