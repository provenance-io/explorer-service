package io.provenance.explorer.grpc.v1

import com.google.protobuf.ProtocolStringList
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.getPaginationBuilder
import io.provenance.metadata.v1.QueryGrpc
import io.provenance.metadata.v1.ValueOwnershipRequest
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class MetadataGrpcClient(channelUri : URI) {

    private val metadataClient: QueryGrpc.QueryBlockingStub

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

        metadataClient = QueryGrpc.newBlockingStub(channel)
    }

    fun getScopesByValueOwner(address: String): ProtocolStringList {
        var offset = 0
        val limit = 100

        val results = metadataClient.valueOwnership(
            ValueOwnershipRequest.newBuilder()
                .setAddress(address)
                .setPagination(getPaginationBuilder(offset, limit))
                .build())

        val total = results.pagination?.total ?: results.scopeUuidsCount.toLong()
        val scopeUuids = results.scopeUuidsList

        while (scopeUuids.count() < total) {
            offset += limit
            metadataClient.valueOwnership(
                ValueOwnershipRequest.newBuilder()
                    .setAddress(address)
                    .setPagination(getPaginationBuilder(offset, limit))
                    .build())
                .let { scopeUuids.addAll(it.scopeUuidsList) }
        }

        return scopeUuids
    }


}

// TODO: Questions
// How far down the chain should I go to record a transaction against an address?
// -> only the signers on the Msg? or delve into the objects to find addresses as well
// -> First level only
// Are the signatures within the TxMsg object also recorded on the Tx itself?
//  -> Seems like they wouldnt be
// What is the execution id, and do I care about it?
//  -> Dont care

// TODO: Steps to add scopes to the platform
// 1) db tables to hold scope, contract, scope specs, contract specs ids and objects
// 2) add async to keep those updated
// 3) Add signature gathering on scope msgs
// 4) update associatedAddresses for scope msgs
//   -> NOTE: signers are addresses ala accounts
//   -> Update for Memorialize and ChangeOwnership
// 5) Add api to fill out NFT pages
// 6) DONE - update asset detail to count nfts with asset as value owner
// 7) ensure account txs show nft txs too -> maybe a separate tx list for nfts?
//
// Per NFT (NEW PAGE)
//- the NFT
//- whatever is under the NFA (I dont know yet)
//- NFT transactions
//  - again no clue what this would look like
//
//Non-Fungible Token - NFT (NEW PAGE)
//- list of NFTs

//so scopes, records, scope spec, contract spec (all updated), and session, which is a one-off

