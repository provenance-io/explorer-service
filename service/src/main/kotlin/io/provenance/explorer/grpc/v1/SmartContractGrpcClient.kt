package io.provenance.explorer.grpc.v1

import com.google.protobuf.util.JsonFormat
import cosmwasm.wasm.v1.QueryGrpc
import cosmwasm.wasm.v1.QueryOuterClass
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.GrpcLoggingInterceptor
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class SmartContractGrpcClient(
    channelUri: URI,
    private val protoParser: JsonFormat.Parser,
    private val explorerProps: ExplorerProperties
) {

    private val smcClient: QueryGrpc.QueryBlockingStub

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

        smcClient = QueryGrpc.newBlockingStub(channel)
    }

    fun getSmCode(code: Long) =
        try {
            smcClient.code(QueryOuterClass.QueryCodeRequest.newBuilder().setCodeId(code).build())
        } catch (e: Exception) {
            null // handles old, non-migrated stored codes.
        }

    fun getSmContract(contractAddr: String) =
        smcClient.contractInfo(QueryOuterClass.QueryContractInfoRequest.newBuilder().setAddress(contractAddr).build())

    fun getSmContractHistory(contractAddr: String) =
        smcClient.contractHistory(QueryOuterClass.QueryContractHistoryRequest.newBuilder().setAddress(contractAddr).build())
}
