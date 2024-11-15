package io.provenance.explorer.service

import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.domain.entities.NavEventsRecord
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.TxUpdate
import io.provenance.explorer.grpc.extensions.denomAmountToPair
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class NavService {

    fun saveNavs(tx: ServiceOuterClass.GetTxResponse, txInfo: TxData, txUpdate: TxUpdate) = transaction {
        tx.txResponse.eventsList.forEachIndexed { eventOrder, event ->
            if (event.type.equals(io.provenance.marker.v1.EventSetNetAssetValue.getDescriptor().fullName) ||
                event.type.equals(io.provenance.metadata.v1.EventSetNetAssetValue.getDescriptor().fullName)
            ) {
                val attributes = event.attributesList.associate { it.key to it.value.trim('"') }

                val denom = attributes["denom"]
                val scopeId = attributes["scope_id"]
                val priceStr = attributes["price"]
                val volume = attributes["volume"]?.toLongOrNull() ?: 1L
                val source = attributes["source"]

                val (priceAmount, priceDenom) = priceStr?.denomAmountToPair() ?: Pair("", "")

                if ((denom != null || scopeId != null) && priceAmount.isNotEmpty()) {
                    NavEventsRecord.insert(
                        blockHeight = txInfo.blockHeight,
                        blockTime = txInfo.txTimestamp,
                        txHash = txInfo.txHash,
                        eventOrder = eventOrder,
                        eventType = event.type,
                        scopeId = scopeId,
                        denom = denom,
                        priceAmount = priceAmount.toLongOrNull(),
                        priceDenom = priceDenom,
                        volume = volume,
                        source = source ?: ""
                    )
                }
            }
        }
    }
}
