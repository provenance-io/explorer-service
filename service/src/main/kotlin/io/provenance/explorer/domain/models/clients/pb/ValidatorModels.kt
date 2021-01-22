package io.provenance.explorer.domain.models.clients.pb

import io.provenance.explorer.domain.models.clients.PubKey

data class PbValidatorsResponse(val blockHeight: String, val validators: List<PbValidator>)

data class PbValidator(val address: String, val pubKey: PubKey, val proposerPriority: String, val votingPower: String)
