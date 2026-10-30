package io.provenance.explorer.domain.extensions

import io.provenance.explorer.model.base.MetadataAddress
import io.provenance.explorer.model.base.PREFIX_SCOPE_SPECIFICATION
import io.provenance.explorer.model.base.toMAddress
import io.provenance.explorer.model.base.toMAddressScopeSpec
import java.nio.ByteBuffer
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

/**
 * x/ledger dates are days since Unix epoch. Zero/negative means unset.
 */
fun ledgerDaysSinceEpochToDate(days: Int): LocalDate? =
    if (days <= 0) null else LocalDate.ofEpochDay(days.toLong())

/**
 * nft_scope JSON specification_id is usually UUID bytes as base64
 * (e.g. BFTd2Lw1AUeJhvG9Zlrx9j0=). Ledger keys use the scopespec bech32.
 */
fun specificationIdToScopeSpecAddr(specId: String): String? {
    if (specId.isBlank()) return null
    if (specId.startsWith("$PREFIX_SCOPE_SPECIFICATION")) return specId
    return try {
        val decoded = Base64.getDecoder().decode(specId)
        when (decoded.size) {
            16 -> uuidFromBytes(decoded).toMAddressScopeSpec().toString()
            else -> {
                val md = MetadataAddress.fromBytes(decoded)
                if (md.getPrefix() == PREFIX_SCOPE_SPECIFICATION) {
                    md.toString()
                } else {
                    md.getPrimaryUuid().toMAddressScopeSpec().toString()
                }
            }
        }
    } catch (_: Exception) {
        try {
            specId.toMAddress().getPrimaryUuid().toMAddressScopeSpec().toString()
        } catch (_: Exception) {
            null
        }
    }
}

private fun uuidFromBytes(data: ByteArray): UUID {
    val bb = ByteBuffer.wrap(data)
    return UUID(bb.long, bb.long)
}
