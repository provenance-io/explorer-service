package io.provenance.explorer.domain.entities

import io.provenance.explorer.domain.exceptions.InvalidArgumentException
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.models.explorer.Announcement
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

object AnnouncementTable : IntIdTable(name = "announcements") {
    val title = varchar("title", 1000)
    val body = text("body")
    val annTimestamp = datetime("ann_timestamp")
}

class AnnouncementRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AnnouncementRecord>(AnnouncementTable) {

        fun insertOrUpdate(obj: Announcement) = transaction {
            obj.id?.let { objId ->
                findById(objId)?.apply {
                    if (obj.body != null) this.body = obj.body
                    if (obj.title != null) this.title = obj.title
                }?.id?.value
                    ?: throw InvalidArgumentException("Invalid announcement ID")
            } ?: AnnouncementTable.insertAndGetId {
                it[this.title] = obj.title!!
                it[this.body] = obj.body!!
                it[this.annTimestamp] = DateTime.now(DateTimeZone.UTC)
            }.value
        }

        fun getAnnouncements(offset: Int, limit: Int, fromDate: DateTime?) = transaction {
            AnnouncementRecord
                .find { if (fromDate != null) AnnouncementTable.annTimestamp greaterEq fromDate.startOfDay() else Op.TRUE }
                .orderBy(Pair(AnnouncementTable.annTimestamp, SortOrder.DESC))
                .limit(limit, offset.toLong())
                .toList()
        }

        fun deleteById(id: Int) = transaction { AnnouncementTable.deleteWhere { AnnouncementTable.id eq id } }
    }

    var title by AnnouncementTable.title
    var body by AnnouncementTable.body
    var annTimestamp by AnnouncementTable.annTimestamp
}
