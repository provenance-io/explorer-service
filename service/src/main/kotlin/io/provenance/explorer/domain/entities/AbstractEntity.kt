package io.provenance.explorer.domain.entities

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

// Allows for last_hit and hit_count to be a common thing
abstract class CacheIdTable<T : Comparable<T>>(name: String = "") : IdTable<T>(name) {
    val lastHit = datetime("last_hit")
    val hitCount = integer("hit_count")
}

open class CacheEntityClass<T : Comparable<T>, out E : Entity<T>>(table: CacheIdTable<T>) : EntityClass<T, E>(table)

abstract class CacheEntity<T : Comparable<T>>(id: EntityID<T>) : Entity<T>(id) {
    abstract var lastHit: LocalDateTime
    abstract var hitCount: Int
}

fun <T : Comparable<T>> CacheEntityClass<T, CacheEntity<T>>.updateHitCount(id: T) =
    findById(id)?.apply {
        this.hitCount++
        this.lastHit = LocalDateTime.now()
    }
