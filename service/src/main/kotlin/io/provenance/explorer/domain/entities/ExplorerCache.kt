package io.provenance.explorer.domain.entities

import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.models.explorer.Spotlight
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime


object SpotlightCacheTable : IdTable<Int>(name = "spotlight_cache") {
    override val id = integer("id").entityId()
    val spotlight = jsonb<SpotlightCacheTable, Spotlight>("spotlight", OBJECT_MAPPER)
    val lastHit = datetime("last_hit")
}

class SpotlightCacheRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SpotlightCacheRecord>(SpotlightCacheTable) {
        fun getIndex() = transaction {
            SpotlightCacheRecord.findById(1)
        }

        fun insertIgnore(json: Spotlight) = transaction {
            (getIndex() ?: new(1) {}).apply {
                this.spotlight = json
                this.lastHit = DateTime.now()
            }
        }
    }

    var spotlight by SpotlightCacheTable.spotlight
    var lastHit by SpotlightCacheTable.lastHit
}
