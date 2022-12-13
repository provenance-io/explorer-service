package io.provenance.explorer.domain.models.explorer

import cosmos.feegrant.v1beta1.Feegrant
import io.provenance.explorer.domain.extensions.toCoinStrList
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.model.BasicAllowance

//region Feegrant

fun Feegrant.BasicAllowance.toDto() = BasicAllowance(this.spendLimitList.toCoinStrList(), this.expiration.toDateTime())

//endregion
