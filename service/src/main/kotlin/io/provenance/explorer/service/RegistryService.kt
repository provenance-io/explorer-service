package io.provenance.explorer.service

import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.TxUpdate
import org.springframework.stereotype.Service

@Service
class RegistryService {

    protected val logger = logger(RegistryService::class)

    fun saveRegistry(txInfo: TxData, tx: ServiceOuterClass.GetTxResponse, txUpdate: TxUpdate) {
        logger.info("TODO: saveRegistry implementation - Hello World")
        println("TODO: saveRegistry implementation - Hello World")
    }
}
