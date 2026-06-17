package io.provenance.explorer.domain.entities

import io.provenance.explorer.domain.models.explorer.pulse.EntityType
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

object LedgerEntityTable : IntIdTable(name = "ledger_entity") {
    val uuid = varchar("uuid", 64)
    val name = varchar("name", 64)
    val type: Column<EntityType> = enumerationByName("type", 64, EntityType::class)
    val dataSource = varchar("data_source", 32)
    val marketId = integer("market_id").nullable()
    val usdPricingExponent = integer("usd_pricing_exponent").nullable()
    val active = bool("active").default(true)
}

class LedgerEntityRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LedgerEntityRecord>(
        LedgerEntityTable
    ) {
        fun findByUuid(uuid: String) = transaction {
            LedgerEntityRecord.find { LedgerEntityTable.uuid eq uuid }.firstOrNull()
        }

        fun findActiveByUuid(uuid: String) = transaction {
            LedgerEntityRecord.find {
                (LedgerEntityTable.uuid eq uuid) and (LedgerEntityTable.active eq true)
            }.firstOrNull()
        }

        fun findByType(type: EntityType) = transaction {
            LedgerEntityRecord.find { LedgerEntityTable.type eq type }.toList()
        }

        fun findActiveByType(entityType: EntityType) = transaction {
            LedgerEntityRecord.find {
                (LedgerEntityTable.type eq entityType) and (LedgerEntityTable.active eq true)
            }.toList()
        }

        fun getAllPaginated(offset: Int, limit: Int) = transaction {
            LedgerEntityRecord.all()
                .limit(limit, offset.toLong())
                .toList()
        }

        fun getActivePaginated(offset: Int, limit: Int) = transaction {
            LedgerEntityRecord.find { LedgerEntityTable.active eq true }
                .limit(limit, offset.toLong())
                .toList()
        }

        fun countActive() = transaction {
            LedgerEntityRecord.find { LedgerEntityTable.active eq true }.count()
        }
    }

    var uuid by LedgerEntityTable.uuid
    var name by LedgerEntityTable.name
    var type by LedgerEntityTable.type
    var dataSource by LedgerEntityTable.dataSource
    var marketId by LedgerEntityTable.marketId
    var usdPricingExponent by LedgerEntityTable.usdPricingExponent
    var active by LedgerEntityTable.active
}

object LedgerEntitySpecTable : IntIdTable(name = "ledger_entity_spec") {
    val entityUuid = varchar("entity_uuid", 64)
    val specificationId = varchar("specification_id", 32)
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
