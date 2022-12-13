package io.provenance.explorer.domain.models.explorer

data class Name(
    val parent: String?,
    val child: String,
    val fullName: String,
    val owner: String,
    val restricted: Boolean,
    val heightAdded: Int
)
