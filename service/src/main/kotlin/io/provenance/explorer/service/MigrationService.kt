package io.provenance.explorer.service

import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.SigJoinType
import io.provenance.explorer.domain.entities.SignatureJoinRecord
import io.provenance.explorer.domain.entities.TransactionCacheRecord
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class MigrationService(private val txService: TransactionService) {

    private fun populateTxSignatures() = transaction {
        TransactionCacheRecord.all().forEach {
            it.txV2.tx.authInfo.signerInfosList.forEach { sig ->
                SignatureJoinRecord.insert(sig.publicKey, SigJoinType.TRANSACTION, it.txV2.txResponse.txhash)
            }
        }
    }

    private fun populateAccSignatures() = transaction {
        AccountRecord.all().forEach {
            SignatureJoinRecord.insert(it.baseAccount.pubKey, SigJoinType.ACCOUNT, it.id.value)
        }
    }

    fun populateSigs(): Boolean {
        populateTxSignatures()
        populateAccSignatures()
        return true
    }

    fun populateTxs(): Boolean {
        listOf(411235,481748,481743,481366,481281,481270,480833,450509,450486,450016,431763,431505,411092,50714,
            2945,411120,3054,3045,342998,364489,84706,50645,415223,363906,345709,50504,415209,2992,346641,2993,
            411203,50672,411357,363826,2962,50618,411424,72132,363847,363671,326913,345800,209092,100866)
            .forEach { txService.tryAddTxs(it) }
        return true
    }

}
