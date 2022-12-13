package io.provenance.explorer.model

data class NameObj(
    val nameList: List<String>,
    val owner: String,
    val restricted: Boolean,
    val fullName: String,
    val childCount: Int
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
