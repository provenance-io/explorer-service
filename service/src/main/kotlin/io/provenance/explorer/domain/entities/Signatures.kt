package io.provenance.explorer.domain.entities

import com.google.protobuf.Any
import cosmos.crypto.multisig.Keys
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.extensions.toBase64
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object SignatureTable : IntIdTable(name = "signature") {
    val base64Sig = varchar("base_64_sig", 128)
    val pubkeyType = varchar("pubkey_type", 128)
    val pubkeyObject = jsonb<SignatureTable, Any>("pubkey_object", OBJECT_MAPPER)
    val multiSigObject = jsonb<SignatureTable, Any>("multi_sig_object", OBJECT_MAPPER).nullable()
}

class SignatureRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SignatureRecord>(SignatureTable) {

        private fun findByBase64Sig(sig: String, type: String) = transaction {
            SignatureRecord.find { (SignatureTable.base64Sig eq sig) and (SignatureTable.pubkeyType eq type) }
                .firstOrNull()
        }

        fun findByIdList(sigIds: List<Int>) = SignatureRecord.find { SignatureTable.id inList sigIds }

        fun findByJoin(joinType: SigJoinType, joinKey: String) = transaction {
            SignatureRecord.wrapRows(
                SignatureTable.join(SJT, JoinType.INNER, SignatureTable.id, SJT.signatureId)
                    .select { SJT.joinType eq joinType.name }
                    .andWhere { SJT.joinKey eq joinKey }
            ).toList()
        }

        fun insertAndGetIds(pubkey: Any, multiSig: Any?): List<Int> = transaction {
            when {
                pubkey.typeUrl.contains("secp256k1") ->
                    pubkey.unpack(cosmos.crypto.secp256k1.Keys.PubKey::class.java)
                        .let { key -> listOf(insertAndGetId(key.key.toBase64(), pubkey.typeUrl, pubkey, multiSig)) }
                pubkey.typeUrl.contains("secp256r1") ->
                    pubkey.unpack(cosmos.crypto.secp256r1.Keys.PubKey::class.java)
                        .let { key -> listOf(insertAndGetId(key.key.toBase64(), pubkey.typeUrl, pubkey, multiSig)) }
                pubkey.typeUrl.contains("ed25519") ->
                    pubkey.unpack(cosmos.crypto.ed25519.Keys.PubKey::class.java)
                        .let { key -> listOf(insertAndGetId(key.key.toBase64(), pubkey.typeUrl, pubkey, multiSig)) }
                pubkey.typeUrl.contains("LegacyAminoPubKey") ->
                    pubkey.unpack(Keys.LegacyAminoPubKey::class.java).let { multi ->
                        multi.publicKeysList
                            .flatMap { insertAndGetIds(it, pubkey) }
                            .toList()
                    }
                else -> listOf()
            }
        }

        private fun insertAndGetId(sig: String, type: String, key: Any, multisig: Any?) =
            findByBase64Sig(sig, type)?.id?.value ?: SignatureTable.insertAndGetId {
                it[this.base64Sig] = sig
                it[this.pubkeyType] = type
                it[this.pubkeyObject] = key
                it[this.multiSigObject] = multisig
            }.value
    }

    var base64Sig by SignatureTable.base64Sig
    var pubkeyType by SignatureTable.pubkeyType
    var pubkeyObject by SignatureTable.pubkeyObject
    var multiSigObject by SignatureTable.multiSigObject
}

enum class SigJoinType { ACCOUNT, TRANSACTION }

val SJT = SignatureJoinTable
object SignatureJoinTable : IntIdTable(name = "signature_join") {
    val joinType = varchar("join_type", 128)
    val joinKey = varchar("join_key", 128)
    val signatureId = integer("signature_id")
}

class SignatureJoinRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SignatureJoinRecord>(SignatureJoinTable) {

        private fun findByJoinAndSigId(sigId: Int, joinType: SigJoinType, joinKey: String) = transaction {
            SignatureJoinRecord.find {
                (SJT.joinType eq joinType.name) and (SJT.joinKey eq joinKey) and (SJT.signatureId eq sigId)
            }
        }

        fun insert(pubkey: Any, joinType: SigJoinType, joinKey: String) = transaction {
            SignatureRecord.insertAndGetIds(pubkey, null).forEach { sigId ->
                findByJoinAndSigId(sigId, joinType, joinKey).firstOrNull() ?: SJT.insert {
                    it[this.joinType] = joinType.name
                    it[this.joinKey] = joinKey
                    it[this.signatureId] = sigId
                }
            }
        }
    }

    var joinType by SignatureJoinTable.joinType
    var joinKey by SignatureJoinTable.joinKey
    var signatureId by SignatureJoinTable.signatureId
}
