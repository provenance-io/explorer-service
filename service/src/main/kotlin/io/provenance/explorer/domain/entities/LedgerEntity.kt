package io.provenance.explorer.domain.entities

import io.provenance.explorer.domain.models.explorer.pulse.EntityType
import io.provenance.metadata.v1.PartyType
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction

object LedgerEntityTable : IntIdTable(name = "ledger_entity") {
    val uuid = varchar("uuid", 128)
    val address = varchar("address", 128)
    val name = varchar("name", 128)
    val type: Column<EntityType> = enumerationByName("type", 128, EntityType::class)
    val ownerType: Column<PartyType> = enumerationByName("owner_type", 128, PartyType::class)
    val usdPricingExponent = integer("usd_pricing_exponent")
}

class LedgerEntityRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LedgerEntityRecord>(
        LedgerEntityTable
    ) {
        fun findByUuid(uuid: String) = transaction {
            LedgerEntityRecord.find { LedgerEntityTable.uuid eq uuid }.firstOrNull()
        }

        fun findByAddress(address: String) = transaction {
            LedgerEntityRecord.find { LedgerEntityTable.address eq address }.firstOrNull()
        }

        fun findByEntityId(entityId: String) = transaction {
            LedgerEntityRecord.find {
                (LedgerEntityTable.uuid eq entityId) or (LedgerEntityTable.address eq entityId)
            }.firstOrNull()
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
    var address by LedgerEntityTable.address
    var name by LedgerEntityTable.name
    var type by LedgerEntityTable.type
    var ownerType by LedgerEntityTable.ownerType
    var usdPricingExponent by LedgerEntityTable.usdPricingExponent
}

object LedgerEntitySpecTable : IntIdTable(name = "ledger_entity_spec") {
    val type: Column<EntityType> = enumerationByName("type", 128, EntityType::class)
    val specificationId = varchar("specification_id", 128)
}

class LedgerEntitySpecRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LedgerEntitySpecRecord>(
        LedgerEntitySpecTable
    ) {
        fun findByType(type: EntityType) = transaction {
            LedgerEntitySpecRecord.find { LedgerEntitySpecTable.type eq type }.toList()
        }
    }

    var type by LedgerEntitySpecTable.type
    var specificationId by LedgerEntitySpecTable.specificationId
}
