package io.provenance.explorer.domain.models.explorer

import io.provenance.explorer.domain.entities.NftScopeRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.model.PartyAndRole
import io.provenance.explorer.model.RecordInputOutput
import io.provenance.explorer.model.SpecDescrip
import io.provenance.metadata.v1.Description
import io.provenance.metadata.v1.Party
import io.provenance.metadata.v1.RecordOutput

fun Description.toSpecDescrip() = SpecDescrip(this.name, this.description, this.websiteUrl, this.iconUrl)

fun List<Party>.toOwnerRoles() = this.map { PartyAndRole(it.address, it.role.name) }

fun RecordOutput.toDataObject(name: String) = RecordInputOutput(name, this.hash, this.status.name)

data class NftVOTransferObj(
    val scope: NftScopeRecord,
    val address: String,
    val tx: TxCacheRecord
)
