package io.provenance.explorer.service

import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.TxUpdate
import org.springframework.stereotype.Service

@Service
class LedgerService {

    protected val logger = logger(LedgerService::class)

    fun saveLedger(txInfo: TxData, tx: ServiceOuterClass.GetTxResponse, txUpdate: TxUpdate) {
        for (message in tx.tx.body.messagesList) {
            logger.info("Message type: ${message.typeUrl}")
            if (!message.typeUrl.contains("ledger")) {
                continue
            }
            when (message.typeUrl) {
                "/provenance.ledger.v1.MsgAddLedgerClassEntryTypeRequest" -> {
                    logger.info("Saving Ledger Class Entry Type")
                }
                "/provenance.ledger.v1.MsgAddLedgerClassBucketTypeRequest" -> {
                    logger.info("Saving Ledger Class Bucket Type")
                }
                "/provenance.ledger.v1.MsgAddLedgerClassStatusTypeRequest" -> {
                    logger.info("Saving Ledger Class Status Type")
                }
                "/provenance.ledger.v1.MsgCreateLedgerClassRequest" -> {
                    logger.info("Saving Ledger Class")
                }
                "/provenance.ledger.v1.MsgCreateRequest" -> {
                    logger.info("Saving Ledger")
                }
                "/provenance.ledger.v1.MsgAppendRequest" -> {
                    logger.info("Saving Ledger Update")
                }
                else -> logger.info("Message type: ${message.typeUrl} not yet implemented")
            }
        }
        for (event in tx.txResponse.eventsList) {
            if (!event.type.contains("ledger")) { continue }
            logger.info("Event type: ${event.type}")
            when (event.type) {
                "provenance.ledger.v1.EventLedgerCreated" -> {
                    logger.info("Saving Ledger")
                }
                "provenance.ledger.v1.EventLedgerEntryAdded" -> {
                    logger.info("Saving Ledger Entry")
                }
                else -> logger.info("Event type: ${event.type} not yet implemented")
            }
        }
    }
}
