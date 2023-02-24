package io.provenance.explorer.domain.entities

import io.provenance.explorer.domain.extensions.execAndMap
import io.provenance.explorer.domain.models.explorer.Name
import io.provenance.explorer.model.NameObj
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet

object NameTable : IntIdTable(name = "name") {
    val parent = varchar("parent", 550).nullable()
    val child = varchar("child", 40)
    val fullName = varchar("full_name", 600)
    val owner = varchar("owner", 128)
    val restricted = bool("restricted").default(false)
    val heightAdded = integer("height_added")
}

class NameRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<NameRecord>(NameTable) {

        fun insertOrUpdate(obj: Name) = transaction {
            getByFullNameAndOwner(obj.fullName, obj.owner)?.apply {
                if (obj.heightAdded > this.heightAdded) {
                    this.restricted = obj.restricted
                    this.heightAdded = obj.heightAdded
                }
            } ?: NameTable.insertIgnore {
                it[this.parent] = obj.parent
                it[this.child] = obj.child
                it[this.fullName] = obj.fullName
                it[this.owner] = obj.owner
                it[this.restricted] = obj.restricted
                it[this.heightAdded] = obj.heightAdded
            }
        }

        fun deleteByFullNameAndOwner(fullName: String, owner: String, blockHeight: Int) = transaction {
            NameTable.deleteWhere {
                (NameTable.fullName eq fullName) and
                    (NameTable.owner eq owner) and
                    (NameTable.heightAdded lessEq blockHeight)
            }
        }

        fun getByFullNameAndOwner(fullName: String, owner: String) = transaction {
            NameRecord.find { (NameTable.fullName eq fullName) and (NameTable.owner eq owner) }.firstOrNull()
        }

        fun getAllNamesForParent(parent: String) = transaction {
            NameRecord.find { NameTable.parent like "%$parent" }.toList()
        }

        fun getNameSet() = transaction {
            val query = """
                with data as (
                    select
                        string_to_array(name.full_name, '.') as parts,
                        owner,
                        restricted,
                        full_name
                    from name
                 ),
                 child_count AS (
                    select 
                        parent.full_name,
                        count(child.id) as child_count
                    from name parent
                    LEFT JOIN name child ON parent.full_name = child.parent
                    group by parent.full_name
                 )
                select
                  array(
                    select parts[i]
                    from generate_subscripts(parts, 1) as indices(i)
                    order by i desc
                  ) as reversed,
                       data.owner,
                       data.restricted,
                       data.full_name,
                       child_count.child_count
                from data
                join child_count ON data.full_name = child_count.full_name
                ;
            """.trimIndent()
            query.execAndMap { it.toNameObj() }
        }
    }

    var parent by NameTable.parent
    var child by NameTable.child
    var fullName by NameTable.fullName
    var owner by NameTable.owner
    var restricted by NameTable.restricted
    var heightAdded by NameTable.heightAdded
}

fun ResultSet.toNameObj() = NameObj(
    (this.getArray("reversed").array as Array<out String>).toList(),
    this.getString("owner"),
    this.getBoolean("restricted"),
    this.getString("full_name"),
    this.getInt("child_count")
)
