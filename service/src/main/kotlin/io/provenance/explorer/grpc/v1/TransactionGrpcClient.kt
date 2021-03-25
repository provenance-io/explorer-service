package io.provenance.explorer.grpc.v1

import cosmos.base.abci.v1beta1.Abci
import cosmos.staking.v1beta1.QueryOuterClass
import cosmos.staking.v1beta1.Staking
import cosmos.tx.v1beta1.ServiceGrpc
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.getPaginationBuilder
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class TransactionGrpcClient(channelUri: URI) {

    private val txClient: ServiceGrpc.ServiceBlockingStub

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

        txClient = ServiceGrpc.newBlockingStub(channel)
    }

    fun getTxByHash(hash: String) = txClient.getTx(ServiceOuterClass.GetTxRequest.newBuilder().setHash(hash).build())

    fun getTxsByHeight(height: Int, total: Int): Pair<MutableList<TxOuterClass.Tx>, MutableList<Abci.TxResponse>> {
        var offset = 0
        val limit = 100

        val results = txClient.getTxsEvent(
            ServiceOuterClass.GetTxsEventRequest.newBuilder()
                .addEvents("tx.height=$height")
                .setPagination(getPaginationBuilder(offset, limit))
                .build())

        val txs = results.txsList
        val txResps = results.txResponsesList

        while (txs.count() < total) {
            offset += limit
            txClient.getTxsEvent(
                ServiceOuterClass.GetTxsEventRequest.newBuilder()
                    .addEvents("tx.height=$height")
                    .setPagination(getPaginationBuilder(offset, limit))
                    .build())
                .let {
                    txs.addAll(it.txsList)
                    txResps.addAll(it.txResponsesList)
                }
        }

        return txs to txResps
    }

}
