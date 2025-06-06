package io.provenance.explorer.domain.entities

import io.provenance.explorer.domain.models.explorer.pulse.EntityType
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.transactions.transaction

object LedgerEntityTable : IntIdTable(name = "ledger_entity") {
    val uuid = varchar("uuid", 128)
    val name = varchar("name", 128)
    val dataSource = varchar("data_source", 128)
    val type: Column<EntityType> = enumerationByName("type", 128, EntityType::class)
    val usdPricingExponent = integer("usd_pricing_exponent").nullable()
}

class LedgerEntityRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LedgerEntityRecord>(
        LedgerEntityTable
    ) {
        fun findByUuid(uuid: String) = transaction {
            LedgerEntityRecord.find { LedgerEntityTable.uuid eq uuid }.firstOrNull()
        }

        fun findByType(type: EntityType) = transaction {
            LedgerEntityRecord.find { LedgerEntityTable.type eq type }.toList()
        }

        fun getAllPaginated(offset: Int, limit: Int) = transaction {
            LedgerEntityRecord.all()
                .limit(limit, offset.toLong())
                .toList()
        }
    }

    var uuid by LedgerEntityTable.uuid
    var name by LedgerEntityTable.name
    var type by LedgerEntityTable.type
    var dataSource by LedgerEntityTable.dataSource
    var usdPricingExponent by LedgerEntityTable.usdPricingExponent
}

object LedgerEntitySpecTable : IntIdTable(name = "ledger_entity_spec") {
    val entityUuid = varchar("entity_uuid", 128)
    val specificationId = varchar("specification_id", 128)
}

class LedgerEntitySpecRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LedgerEntitySpecRecord>(
        LedgerEntitySpecTable
    ) {
        fun findByUuid(uuid: String) = transaction {
            LedgerEntitySpecRecord.find { LedgerEntitySpecTable.entityUuid eq uuid }.toList()
        }
    }

    var entityUuid by LedgerEntitySpecTable.entityUuid
    var specificationId by LedgerEntitySpecTable.specificationId
}
