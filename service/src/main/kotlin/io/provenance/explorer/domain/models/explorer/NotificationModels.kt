package io.provenance.explorer.domain.models.explorer

import io.provenance.explorer.domain.entities.AnnouncementRecord
import io.swagger.annotations.ApiModelProperty

data class Announcement(
    @ApiModelProperty(value = "Record ID for existing announcement", required = false)
    val id: Int?,
    @ApiModelProperty(value = "Title for announcement; required for new entry", required = false)
    val title: String?,
    @ApiModelProperty(value = "Body for announcement; required for new entry", required = false)
    val body: String?
) {
    fun upsert() = AnnouncementRecord.insertOrUpdate(this)
}
