package io.provenance.explorer.domain.entities

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Paths

abstract class BaseDbTest {

    companion object {
        init {
            Database.connect("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
            transaction {
                var sql = this::class.java.getResource("/db/migration/V1_96__Add_nav_event_table.sql")!!
                    .readText()
                sql = sql
                    .replace("TIMESTAMPTZ", "TIMESTAMP")
                    .replace("TEXT", "VARCHAR(255)")
                exec(sql)

//                this::class.java.getResource("/db/migration/V1_98__Rename_Keyword_Columns.sql")!!
//                    .readText().apply {
//                        println(this)
//                        exec(this)
//                    }

//                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'NAV_EVENTS';".let {
//                    exec(it) {
//                        while (it.next()) {
//                            println(it.getString(1))
//                        }
//                    }
//                }
            }
        }
    }

    fun executeSqlFile(filePath: String) {
        val path = Paths.get(filePath)
        if (!Files.exists(path)) {
            throw IllegalArgumentException("SQL file not found: $filePath")
        }
        val sqlStatements = Files.readString(path)
        transaction {
            exec(sqlStatements)
        }
    }
}
