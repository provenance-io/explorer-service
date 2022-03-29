package io.provenance.explorer.domain.models.explorer

import io.provenance.explorer.domain.entities.AnnouncementRecord
import io.swagger.annotations.ApiModelProperty
import org.joda.time.DateTime

data class OpenProposals(
    val totalOpenCount: Int,
    val upgradeOpenCount: Int
)

data class ScheduledUpgrade(
    val proposalId: Long,
    val upgradePlan: String,
    val upgradeName: String,
    val upgradeVersion: String,
    val upgradeHeight: Long,
    val approximateTime: DateTime
)

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

data class AnnouncementOut(
    val id: Int,
    val title: String,
    val body: String,
    val timestamp: String
)
