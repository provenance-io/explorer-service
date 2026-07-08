package io.provenance.explorer.domain.models.explorer

import io.swagger.v3.oas.annotations.media.Schema

data class Announcement(
    @Schema(description = "Record ID for existing announcement", required = false)
    val id: Int?,
    @Schema(description = "Title for announcement; required for new entry", required = false)
    val title: String?,
    @Schema(description = "Body for announcement; required for new entry", required = false)
    val body: String?
)
