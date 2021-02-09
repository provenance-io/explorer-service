package io.provenance.explorer.domain.models.clients.pb

import io.provenance.explorer.domain.models.clients.CustomPubKey
import io.provenance.explorer.domain.models.clients.Pagination

data class PbValidatorsResponse(val blockHeight: String, val validators: List<PbValidator>, val pagination: Pagination?)

data class PbValidator(val address: String, val pubKey: CustomPubKey, val proposerPriority: String, val votingPower: String)
