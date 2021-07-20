package io.provenance.explorer.config

import io.provenance.explorer.domain.core.logger
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.sql.Connection
import javax.sql.DataSource

@Configuration
class DataConfig {

    protected val logger = logger(DataConfig::class)

    @Bean("databaseConnect")
    fun dataConnect(dataSource: DataSource): Database =
        Database.connect(dataSource)
            .also { TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED }

    @Bean
    fun flyway(dataSource: DataSource): Flyway {
        return Flyway(FluentConfiguration().dataSource(dataSource))
    }

    @Bean
    fun flywayInitializer(flyway: Flyway): FlywayMigrationInitializer = FlywayMigrationInitializer(flyway)

    @Bean
    @Profile("!test")
    fun flywayMigration(dataSource: DataSource, flyway: Flyway): Int {
        flyway.info().all().forEach { logger.info("Flyway migration: ${it.script}") }
        return flyway.migrate().migrationsExecuted
    }

    @Bean("flywayMigration")
    @Profile("test")
    fun flywayMigrationTest(dataSource: DataSource, flyway: Flyway): Int {
        flyway.info().all().forEach { logger.info("Flyway migration: ${it.script}") }
        flyway.clean()
        return flyway.migrate().migrationsExecuted
    }
}
