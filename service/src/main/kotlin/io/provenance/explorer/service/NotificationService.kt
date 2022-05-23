package io.provenance.explorer.service

import cosmos.gov.v1beta1.Gov
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AnnouncementRecord
import io.provenance.explorer.domain.entities.GovProposalRecord
import io.provenance.explorer.domain.entities.getProposalType
import io.provenance.explorer.domain.exceptions.InvalidArgumentException
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.Announcement
import io.provenance.explorer.domain.models.explorer.AnnouncementOut
import io.provenance.explorer.domain.models.explorer.OpenProposals
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.ScheduledUpgrade
import io.provenance.explorer.grpc.v1.GovGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class NotificationService(
    private val govService: GovService,
    private val govClient: GovGrpcClient,
    private val props: ExplorerProperties,
    private val cacheService: CacheService,
    private val blockService: BlockService
) {
    protected val logger = logger(NotificationService::class)

    fun fetchOpenProposalBreakdown() = transaction {
        val proposals = GovProposalRecord.getNonFinalProposals().sortedBy { it.proposalId }

        val typeUrl = govService.getUpgradeProtoType()
        val (upgrades, nonUpgrades) = proposals.map {
            it.proposalType to AnnouncementOut(it.proposalId.toInt(), it.title, null, null, null, null)
        }.partition { it.first == typeUrl.getProposalType() }

        OpenProposals(nonUpgrades.map { it.second }, upgrades.map { it.second })
    }

    fun fetchScheduledUpgrades() = transaction {
        val upgrades = GovProposalRecord.findByProposalType(govService.getUpgradeProtoType())
            .filter { it.status == Gov.ProposalStatus.PROPOSAL_STATUS_PASSED.name }
            .filter { proposal ->
                val name = proposal.content.get("plan").get("name").asText()
                govClient.getIfUpgradeApplied(name) == null
            }.sortedBy { it.proposalId }

        val avgBlockTime = cacheService.getAvgBlockTime()
        val currentHeight = blockService.getLatestBlockHeightIndexOrFromChain()
        val currentTimeMs = DateTime.now(DateTimeZone.UTC).millis

        upgrades.map {
            val plannedHeight = it.content.get("plan").get("height").asLong()
            val heightDiff = plannedHeight - currentHeight
            val additionalMs = avgBlockTime.multiply(BigDecimal(1000)).multiply(BigDecimal(heightDiff)).toLong()
            val approxUpgrade = DateTime(currentTimeMs + additionalMs, DateTimeZone.UTC)

            ScheduledUpgrade(
                it.proposalId,
                it.title,
                it.content.get("plan").get("name").asText(),
                it.content.get("plan").get("info").asText().getChainVersionFromUrl(props.upgradeVersionRegex),
                plannedHeight,
                approxUpgrade
            )
        }
    }

    fun upsertAnnouncement(obj: Announcement) = transaction {
        obj.validateAnnouncement().upsert()
    }

    private fun Announcement.validateAnnouncement(): Announcement {
        if (this.id == null && (this.title == null || this.body == null))
            throw InvalidArgumentException("Both title and announcement body must be filled in for new announcement.")
        return this
    }

    fun getAnnouncements(page: Int, count: Int, fromDate: DateTime?) =
        AnnouncementRecord.getAnnouncements(page.toOffset(count), count, fromDate)
            .let { results ->
                val totalCount = AnnouncementRecord.getAnnouncementCount(fromDate)
                PagedResults(totalCount.pageCountOfResults(count), results, totalCount)
            }

    fun getAnnouncementById(id: Int) = transaction {
        AnnouncementRecord.getById(id) ?: throw ResourceNotFoundException("Invalid announcement id: '$id'")
    }

    fun deleteAnnouncement(id: Int) = AnnouncementRecord.deleteById(id)
}
