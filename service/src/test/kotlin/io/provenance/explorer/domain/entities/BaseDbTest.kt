package io.provenance.explorer.domain.entities

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import java.nio.file.Files
import java.nio.file.Paths

abstract class BaseDbTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupDatabase() {
            Database.connect("jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
            transaction {
                var sql = this::class.java.getResource("/db/migration/V1_96__Add_nav_event_table.sql")!!
                    .readText()
                sql = sql
                    .replace("TIMESTAMPTZ", "TIMESTAMP")
                    .replace("TEXT", "VARCHAR(255)")
                exec(sql)
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
