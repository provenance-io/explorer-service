package io.provenance.explorer.model

import org.joda.time.DateTime

data class OpenProposals(
    val nonUpgradeOpenList: List<AnnouncementOut>,
    val upgradeOpenList: List<AnnouncementOut>
)

data class ScheduledUpgrade(
    val proposalId: Long,
    val upgradePlan: String,
    val upgradeName: String,
    val upgradeVersion: String,
    val upgradeHeight: Long,
    val approximateTime: DateTime
)

data class AnnouncementOut(
    val id: Int,
    val title: String,
    val body: String?,
    val timestamp: String?,
    val prevId: Int?,
    val nextId: Int?
)
