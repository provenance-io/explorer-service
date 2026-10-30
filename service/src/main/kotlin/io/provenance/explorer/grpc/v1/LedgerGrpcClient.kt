package io.provenance.explorer.grpc.v1

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannelBuilder
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.protobuf.ProtoUtils
import io.grpc.stub.ClientCalls
import io.grpc.stub.MetadataUtils
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.BLOCK_HEIGHT
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ChainLedger(
    val ledgerClassId: String,
    val statusTypeId: Int,
    val nextPmtDate: Int,
    val nextPmtAmt: String,
    val interestRate: Int,
    val maturityDate: Int
)

/**
 * x/ledger queries without bumping proto-kotlin (JVM 17). Descriptors match
 * provenance.ledger.v1 Query/Ledger, LedgerClass, and LedgerClassStatusTypes.
 */
@Component
class LedgerGrpcClient(channelUri: URI, private val semaphore: Semaphore) {

    private val channel: Channel
    private val statusCodeByClassId = ConcurrentHashMap<String, Map<Int, String>>()
    private val denomByClassId = ConcurrentHashMap<String, String>()

    init {
        channel =
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
    }

    /**
     * Current x/ledger header for a metadata scope, or null if none exists.
     * Live snapshot only — not used for historical Pulse backfill.
     * [assetClassId] is the scopespec bech32; [nftId] is the scope bech32.
     */
    suspend fun getLedger(
        assetClassId: String,
        nftId: String
    ): ChainLedger? = semaphore.withPermit {
        val request = DynamicMessage.newBuilder(ledgerRequestDesc)
            .setField(
                ledgerRequestDesc.findFieldByName("key"),
                DynamicMessage.newBuilder(ledgerKeyDesc)
                    .setField(ledgerKeyDesc.findFieldByName("nft_id"), nftId)
                    .setField(ledgerKeyDesc.findFieldByName("asset_class_id"), assetClassId)
                    .build()
            )
            .build()
        val response = unary(ledgerMethod, request) ?: return@withPermit null
        val ledger = response.getField(ledgerResponseDesc.findFieldByName("ledger")) as? DynamicMessage
            ?: return@withPermit null
        ChainLedger(
            ledgerClassId = ledger.string("ledger_class_id"),
            statusTypeId = ledger.int("status_type_id"),
            nextPmtDate = ledger.int("next_pmt_date"),
            nextPmtAmt = ledger.string("next_pmt_amt"),
            interestRate = ledger.int("interest_rate"),
            maturityDate = ledger.int("maturity_date")
        )
    }

    suspend fun statusCode(ledgerClassId: String, statusTypeId: Int): String? {
        if (statusTypeId <= 0 || ledgerClassId.isBlank()) return null
        val codes = statusCodeByClassId[ledgerClassId] ?: fetchStatusCodes(ledgerClassId).also {
            statusCodeByClassId.putIfAbsent(ledgerClassId, it)
        }
        return (statusCodeByClassId[ledgerClassId] ?: codes)[statusTypeId] ?: statusTypeId.toString()
    }

    suspend fun classDenom(ledgerClassId: String): String? {
        if (ledgerClassId.isBlank()) return null
        denomByClassId[ledgerClassId]?.let { return it.ifBlank { null } }
        val denom = fetchClassDenom(ledgerClassId).orEmpty()
        denomByClassId.putIfAbsent(ledgerClassId, denom)
        return (denomByClassId[ledgerClassId] ?: denom).ifBlank { null }
    }

    private suspend fun fetchStatusCodes(ledgerClassId: String): Map<Int, String> =
        semaphore.withPermit {
            val request = DynamicMessage.newBuilder(statusTypesRequestDesc)
                .setField(statusTypesRequestDesc.findFieldByName("ledger_class_id"), ledgerClassId)
                .build()
            val response = unary(statusTypesMethod, request) ?: return@withPermit emptyMap()
            val values = response.getField(statusTypesResponseDesc.findFieldByName("status_types"))
            @Suppress("UNCHECKED_CAST")
            (values as List<DynamicMessage>).associate { msg ->
                msg.int("id") to msg.string("code")
            }
        }

    private suspend fun fetchClassDenom(ledgerClassId: String): String? =
        semaphore.withPermit {
            val request = DynamicMessage.newBuilder(classRequestDesc)
                .setField(classRequestDesc.findFieldByName("ledger_class_id"), ledgerClassId)
                .build()
            val response = unary(classMethod, request) ?: return@withPermit null
            val ledgerClass = response.getField(classResponseDesc.findFieldByName("ledger_class")) as? DynamicMessage
            ledgerClass?.string("denom")?.ifBlank { null }
        }

    private fun unary(
        method: MethodDescriptor<DynamicMessage, DynamicMessage>,
        request: DynamicMessage,
        height: Int? = null
    ): DynamicMessage? {
        val callChannel = if (height == null) {
            channel
        } else {
            val headers = io.grpc.Metadata()
            headers.put(
                io.grpc.Metadata.Key.of(BLOCK_HEIGHT, io.grpc.Metadata.ASCII_STRING_MARSHALLER),
                height.toString()
            )
            ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(headers))
        }
        return try {
            ClientCalls.blockingUnaryCall(
                callChannel,
                method,
                CallOptions.DEFAULT.withDeadlineAfter(30, TimeUnit.SECONDS),
                request
            )
        } catch (e: Exception) {
            if (Status.fromThrowable(e).code == Status.Code.NOT_FOUND) null else throw e
        }
    }

    companion object {
        private val fileDesc = buildFileDescriptor()
        private val ledgerKeyDesc = fileDesc.findMessageTypeByName("LedgerKey")
        private val ledgerRequestDesc = fileDesc.findMessageTypeByName("QueryLedgerRequest")
        private val ledgerResponseDesc = fileDesc.findMessageTypeByName("QueryLedgerResponse")
        private val classRequestDesc = fileDesc.findMessageTypeByName("QueryLedgerClassRequest")
        private val classResponseDesc = fileDesc.findMessageTypeByName("QueryLedgerClassResponse")
        private val statusTypesRequestDesc = fileDesc.findMessageTypeByName("QueryLedgerClassStatusTypesRequest")
        private val statusTypesResponseDesc = fileDesc.findMessageTypeByName("QueryLedgerClassStatusTypesResponse")

        private val ledgerMethod = unaryMethod("Ledger", ledgerRequestDesc, ledgerResponseDesc)
        private val classMethod = unaryMethod("LedgerClass", classRequestDesc, classResponseDesc)
        private val statusTypesMethod = unaryMethod(
            "LedgerClassStatusTypes",
            statusTypesRequestDesc,
            statusTypesResponseDesc
        )

        private fun unaryMethod(
            name: String,
            request: Descriptors.Descriptor,
            response: Descriptors.Descriptor
        ): MethodDescriptor<DynamicMessage, DynamicMessage> =
            MethodDescriptor.newBuilder<DynamicMessage, DynamicMessage>()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(
                    MethodDescriptor.generateFullMethodName("provenance.ledger.v1.Query", name)
                )
                .setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(request)))
                .setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(response)))
                .build()

        private fun buildFileDescriptor(): Descriptors.FileDescriptor {
            val typeString = DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING
            val typeInt32 = DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32
            val typeMessage = DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE
            val optional = DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL
            val repeated = DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED

            fun field(
                name: String,
                number: Int,
                type: DescriptorProtos.FieldDescriptorProto.Type,
                label: DescriptorProtos.FieldDescriptorProto.Label = optional,
                typeName: String? = null
            ) = DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName(name)
                .setNumber(number)
                .setType(type)
                .setLabel(label)
                .also { if (typeName != null) it.setTypeName(typeName) }
                .build()

            fun message(name: String, vararg fields: DescriptorProtos.FieldDescriptorProto) =
                DescriptorProtos.DescriptorProto.newBuilder()
                    .setName(name)
                    .addAllField(fields.toList())
                    .build()

            val file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("ledger_query.proto")
                .setPackage("provenance.ledger.v1")
                .setSyntax("proto3")
                .addMessageType(
                    message(
                        "LedgerKey",
                        field("nft_id", 1, typeString),
                        field("asset_class_id", 2, typeString)
                    )
                )
                .addMessageType(
                    message(
                        "Ledger",
                        field("key", 1, typeMessage, typeName = ".provenance.ledger.v1.LedgerKey"),
                        field("ledger_class_id", 2, typeString),
                        field("status_type_id", 3, typeInt32),
                        field("next_pmt_date", 4, typeInt32),
                        field("next_pmt_amt", 5, typeString),
                        field("interest_rate", 6, typeInt32),
                        field("maturity_date", 7, typeInt32)
                    )
                )
                .addMessageType(
                    message(
                        "LedgerClass",
                        field("ledger_class_id", 1, typeString),
                        field("asset_class_id", 2, typeString),
                        field("denom", 3, typeString)
                    )
                )
                .addMessageType(
                    message(
                        "LedgerClassStatusType",
                        field("id", 1, typeInt32),
                        field("code", 2, typeString)
                    )
                )
                .addMessageType(
                    message(
                        "QueryLedgerRequest",
                        field("key", 1, typeMessage, typeName = ".provenance.ledger.v1.LedgerKey")
                    )
                )
                .addMessageType(
                    message(
                        "QueryLedgerResponse",
                        field("ledger", 1, typeMessage, typeName = ".provenance.ledger.v1.Ledger")
                    )
                )
                .addMessageType(
                    message(
                        "QueryLedgerClassRequest",
                        field("ledger_class_id", 1, typeString)
                    )
                )
                .addMessageType(
                    message(
                        "QueryLedgerClassResponse",
                        field("ledger_class", 1, typeMessage, typeName = ".provenance.ledger.v1.LedgerClass")
                    )
                )
                .addMessageType(
                    message(
                        "QueryLedgerClassStatusTypesRequest",
                        field("ledger_class_id", 1, typeString)
                    )
                )
                .addMessageType(
                    message(
                        "QueryLedgerClassStatusTypesResponse",
                        field(
                            "status_types",
                            1,
                            typeMessage,
                            repeated,
                            ".provenance.ledger.v1.LedgerClassStatusType"
                        )
                    )
                )
                .build()

            return Descriptors.FileDescriptor.buildFrom(file, arrayOf())
        }

        private fun DynamicMessage.string(name: String): String =
            (getField(descriptorForType.findFieldByName(name)) as? String).orEmpty()

        private fun DynamicMessage.int(name: String): Int =
            getField(descriptorForType.findFieldByName(name)) as? Int ?: 0
    }
}
