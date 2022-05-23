package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.annotation.HiddenApi
import io.provenance.explorer.domain.models.explorer.Announcement
import io.provenance.explorer.service.NotificationService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.joda.time.DateTime
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.Max
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v2/notifications"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Notification endpoints",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Notification"]
)
class NotificationController(private val notifService: NotificationService) {

    @ApiOperation("Returns list of open proposals, sub count of open upgrade proposals")
    @GetMapping("/proposals")
    fun getOpenProposals() = ResponseEntity.ok(notifService.fetchOpenProposalBreakdown())

    @ApiOperation("Returns list of scheduled upgrades")
    @GetMapping("/upgrades")
    fun getScheduledUpgrades() = ResponseEntity.ok(notifService.fetchScheduledUpgrades())

    @ApiOperation("Insert or update an announcement to be displayed in Explorer")
    @PutMapping("/announcement")
    @HiddenApi
    fun upsertAnnouncement(@RequestBody obj: Announcement): ResponseEntity<String> {
        val id = notifService.upsertAnnouncement(obj)
        val text = if (obj.id == null) "created" else "updated"
        return ResponseEntity.ok("Announcement with ID $id has been $text.")
    }

    @ApiOperation("Returns a paginated list of announcements")
    @GetMapping("/announcement/all")
    fun getAnnouncements(
        @ApiParam(defaultValue = "1", required = false) @RequestParam(defaultValue = "1") @Min(1) page: Int,
        @ApiParam(value = "Record count between 1 and 50", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) count: Int,
        @ApiParam(
            type = "DateTime",
            value = "DateTime format as  `yyyy-MM-dd` — for example, \"2000-10-31\"",
            required = false
        ) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: DateTime?
    ) = ResponseEntity.ok(notifService.getAnnouncements(page, count, fromDate))

    @ApiOperation("Returns a single announcement by ID")
    @GetMapping("/announcement/{id}")
    fun getAnnouncementById(@PathVariable id: Int) = ResponseEntity.ok(notifService.getAnnouncementById(id))

    @ApiOperation("Delete an existing announcement")
    @DeleteMapping("/announcement/{id}")
    @HiddenApi
    fun deleteAnnouncement(@PathVariable id: Int): ResponseEntity<String> {
        notifService.deleteAnnouncement(id)
        return ResponseEntity.ok("Announcement with ID $id has been deleted.")
    }
}
