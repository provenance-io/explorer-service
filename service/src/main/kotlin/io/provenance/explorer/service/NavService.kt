package io.provenance.explorer.service

import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.domain.entities.NavEventsRecord
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.TxUpdate
import io.provenance.explorer.grpc.extensions.denomAmountToPair
import io.provenance.marker.v1.EventSetNetAssetValue
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service


@Service
class NavService {

    fun saveNavs(tx: ServiceOuterClass.GetTxResponse, txInfo: TxData, txUpdate: TxUpdate) = transaction {
        tx.txResponse.eventsList.forEachIndexed{eventOrder, event ->
            if (event.type.equals(EventSetNetAssetValue.getDescriptor().fullName)) {
                val attributes = event.attributesList.associate {
                    it.key to it.value.trim('"')
                }

                val denom = attributes["denom"]
                val priceStr = attributes["price"]
                val volume = attributes["volume"]?.toLongOrNull()
                val source = attributes["source"]

                val (priceAmount, priceDenom) = priceStr?.denomAmountToPair() ?: Pair("", "")

                if (denom != null && volume != null) {
                    NavEventsRecord.insert(
                        blockHeight = txInfo.blockHeight,
                        blockTime = txInfo.txTimestamp,
                        txHash = txInfo.txHash,
                        eventOrder = eventOrder,
                        eventType = event.type,
                        scopeId = null,
                        denom = denom,
                        priceAmount = priceAmount.toLongOrNull(),
                        priceDenom = priceDenom,
                        volume = volume,
                        source = source  ?: ""
                    )
                }
            }
            }
        }
    }
