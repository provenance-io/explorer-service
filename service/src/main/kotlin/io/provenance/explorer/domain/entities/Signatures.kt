package io.provenance.explorer.domain.entities

import com.google.protobuf.Any
import cosmos.crypto.multisig.Keys
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.config.ExplorerProperties.Companion.PROV_ACC_PREFIX
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.core.sql.toProcedureObject
import io.provenance.explorer.domain.extensions.sigToAddress
import io.provenance.explorer.domain.extensions.toBase64
import io.provenance.explorer.domain.extensions.typeToLabel
import io.provenance.explorer.domain.models.explorer.AccountSigData
import io.provenance.explorer.domain.models.explorer.ED_25519
import io.provenance.explorer.domain.models.explorer.LEGACY_MULTISIG
import io.provenance.explorer.domain.models.explorer.SECP_256_K1
import io.provenance.explorer.domain.models.explorer.SECP_256_R1
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.model.TxSignature
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object SignatureTable : IntIdTable(name = "signature") {
    val base64Sig = varchar("base_64_sig", 128).nullable()
    val pubkeyType = varchar("pubkey_type", 128)
    val pubkeyObject = jsonb<SignatureTable, Any>("pubkey_object", OBJECT_MAPPER)
    val address = varchar("address", 128)
}

class SignatureRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SignatureRecord>(SignatureTable) {

        fun findByAddressSingle(addr: String) = transaction {
            SignatureRecord.find { (SignatureTable.address eq addr) }.first()
        }

        fun findByPubkeyObject(pubkey: Any, type: String) = transaction {
            SignatureRecord
                .find { (SignatureTable.pubkeyObject eq pubkey) and (SignatureTable.pubkeyType eq type) }
                .firstOrNull()
        }

        fun findByAddress(addr: String) = transaction {
            SignatureTable
                .leftJoin(SignatureMultiJoinTable, { SignatureTable.id }, { SignatureMultiJoinTable.multiSigId })
                .slice(
                    SignatureTable.pubkeyType,
                    SignatureTable.pubkeyObject,
                    SignatureTable.base64Sig,
                    SignatureMultiJoinTable.sigIdx,
                    SignatureMultiJoinTable.sigAddress
                )
                .select { SignatureTable.address eq addr }
                .orderBy(Pair(SignatureMultiJoinTable.sigIdx, SortOrder.ASC))
                .map {
                    AccountSigData(
                        it[SignatureTable.pubkeyType],
                        it[SignatureTable.pubkeyObject],
                        it[SignatureTable.base64Sig],
                        it[SignatureMultiJoinTable.sigIdx],
                        it[SignatureMultiJoinTable.sigAddress]
                    )
                }
        }

        fun insertAndGet(pubkey: Any, addr: String): Int? = transaction {
            when {
                pubkey.typeUrl.contains(SECP_256_K1) ->
                    pubkey.unpack(cosmos.crypto.secp256k1.Keys.PubKey::class.java)
                        .let { key -> insertAndGet(key.key.toBase64(), pubkey.typeUrl, pubkey, addr) }
                pubkey.typeUrl.contains(SECP_256_R1) ->
                    pubkey.unpack(cosmos.crypto.secp256r1.Keys.PubKey::class.java)
                        .let { key -> insertAndGet(key.key.toBase64(), pubkey.typeUrl, pubkey, addr) }
                pubkey.typeUrl.contains(ED_25519) ->
                    pubkey.unpack(cosmos.crypto.ed25519.Keys.PubKey::class.java)
                        .let { key -> insertAndGet(key.key.toBase64(), pubkey.typeUrl, pubkey, addr) }
                pubkey.typeUrl.contains(LEGACY_MULTISIG) ->
                    pubkey.unpack(Keys.LegacyAminoPubKey::class.java).let { multi ->
                        // Insert multi key
                        insertAndGet(null, pubkey.typeUrl, pubkey, addr).also { multiId ->
                            // for each key within, insert and add join
                            multi.publicKeysList.forEachIndexed { idx, key ->
                                key.sigToAddress(PROV_ACC_PREFIX)?.let { sigAddr ->
                                    insertAndGet(key, sigAddr)?.also { sigId ->
                                        SignatureMultiJoinRecord.insert(multiId, addr, sigId, sigAddr, idx)
                                    }
                                }
                            }
                        }
                    }
                else -> null
            }
        }

        private fun insertAndGet(sig: String?, type: String, key: Any, addr: String): Int =
            findByPubkeyObject(key, type)?.id?.value
                ?: SignatureTable.insertAndGetId {
                    it[this.base64Sig] = sig
                    it[this.pubkeyType] = type
                    it[this.pubkeyObject] = key
                    it[this.address] = addr
                }.value
    }

    var base64Sig by SignatureTable.base64Sig
    var pubkeyType by SignatureTable.pubkeyType
    var pubkeyObject by SignatureTable.pubkeyObject
    var address by SignatureTable.address
}

object SignatureMultiJoinTable : IntIdTable(name = "signature_multi_join") {
    val multiSigId = integer("multi_sig_id")
    val multiSigAddress = varchar("multi_sig_address", 128)
    val sigId = integer("sig_id")
    val sigAddress = varchar("sig_address", 128)
    val sigIdx = integer("sig_idx")
}

class SignatureMultiJoinRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SignatureMultiJoinRecord>(SignatureMultiJoinTable) {

        fun insert(multiSigId: Int, multiSigAddress: String, sigId: Int, sigAddr: String, idx: Int) = transaction {
            SignatureMultiJoinTable.insertIgnore {
                it[this.multiSigId] = multiSigId
                it[this.multiSigAddress] = multiSigAddress
                it[this.sigId] = sigId
                it[this.sigAddress] = sigAddr
                it[this.sigIdx] = idx
            }
        }
    }

    var multiSigId by SignatureMultiJoinTable.multiSigId
    var multiSigAddress by SignatureMultiJoinTable.multiSigAddress
    var sigId by SignatureMultiJoinTable.sigId
    var sigAddress by SignatureMultiJoinTable.sigAddress
    var sigIdx by SignatureMultiJoinTable.sigIdx
}

object SignatureTxTable : IntIdTable(name = "signature_tx") {
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val blockHeight = integer("block_height")
    val txHash = varchar("tx_hash", 64)
    val sigIdx = integer("sig_idx")
    val sigId = integer("sig_id")
    val sequence = integer("sequence")
}

class SignatureTxRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SignatureTxRecord>(SignatureTxTable) {

        fun findByTxHashId(txHashId: Int) = transaction {
            SignatureTxTable
                .innerJoin(SignatureTable, { SignatureTxTable.sigId }, { SignatureTable.id })
                .slice(
                    SignatureTxTable.sigIdx,
                    SignatureTxTable.sequence,
                    SignatureTable.pubkeyType,
                    SignatureTable.address
                )
                .select { SignatureTxTable.txHashId eq txHashId }
                .orderBy(Pair(SignatureTxTable.sigIdx, SortOrder.ASC))
                .map {
                    TxSignature(
                        it[SignatureTxTable.sigIdx],
                        it[SignatureTable.pubkeyType].typeToLabel(),
                        it[SignatureTable.address],
                        it[SignatureTxTable.sequence]
                    )
                }
        }

        fun buildInsert(pubkey: Any, sigIdx: Int, txInfo: TxData, sequence: Int) = transaction {
            SignatureRecord.insertAndGet(pubkey, pubkey.sigToAddress(PROV_ACC_PREFIX)!!)
                ?.let { sigId ->
                    listOf(
                        0,
                        0,
                        txInfo.blockHeight,
                        txInfo.txHash,
                        sigIdx,
                        sigId,
                        sequence
                    ).toProcedureObject()
                }
        }
    }

    var txHashId by TxCacheRecord referencedOn SignatureTxTable.txHashId
    var blockHeight by SignatureTxTable.blockHeight
    var txHash by SignatureTxTable.txHash
    var sigIdx by SignatureTxTable.sigIdx
    var sigId by SignatureTxTable.sigId
    var sequence by SignatureTxTable.sequence
}
