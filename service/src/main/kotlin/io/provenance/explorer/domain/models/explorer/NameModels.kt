package io.provenance.explorer.domain.models.explorer

data class Name(
    val parent: String?,
    val child: String,
    val fullName: String,
    val owner: String,
    val restricted: Boolean,
    val heightAdded: Int
)

data class NameObj(
    val nameList: List<String>,
    val owner: String,
    val restricted: Boolean,
    val fullName: String
)

data class NameMap(
    val segmentName: String,
    val children: MutableList<NameMap>,
    val fullName: String,
    var owner: String? = null,
    var restricted: Boolean = false
)

data class NameTreeResponse(
    val tree: MutableList<NameMap>,
    val depthCount: Int
)
