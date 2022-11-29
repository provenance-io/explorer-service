package io.provenance.explorer.grpc.v1

import com.google.protobuf.util.JsonFormat
import cosmwasm.wasm.v1.QueryGrpcKt
import cosmwasm.wasm.v1.queryCodeRequest
import cosmwasm.wasm.v1.queryContractHistoryRequest
import cosmwasm.wasm.v1.queryContractInfoRequest
import cosmwasm.wasm.v1.queryParamsRequest
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class SmartContractGrpcClient(
    channelUri: URI,
    private val protoParser: JsonFormat.Parser,
    private val explorerProps: ExplorerProperties
) {

    private val smcClient: QueryGrpcKt.QueryCoroutineStub

    init {
        val channel =
            ManagedChannelBuilder.forAddress(channelUri.host, channelUri.port)
                .also {
                    if (channelUri.scheme == "grpcs") {
                        it.useTransportSecurity()
                    } else {
                        it.usePlaintext()
                    }
                }
                .idleTimeout(60, TimeUnit.SECONDS)
                .keepAliveTime(10, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .intercept(GrpcLoggingInterceptor())
                .build()

        smcClient = QueryGrpcKt.QueryCoroutineStub(channel)
    }

    suspend fun getSmCode(code: Long) =
        try {
            smcClient.code(queryCodeRequest { this.codeId = code })
        } catch (e: Exception) {
            null // handles old, non-migrated stored codes.
        }

    suspend fun getSmContract(contractAddr: String) =
        smcClient.contractInfo(queryContractInfoRequest { this.address = contractAddr })

    suspend fun getSmContractHistory(contractAddr: String) =
        smcClient.contractHistory(queryContractHistoryRequest { this.address = contractAddr })

    suspend fun getWasmParams() = smcClient.params(queryParamsRequest { })
}
