package io.provenance.explorer.config

import feign.Feign
import feign.Request
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import io.provenance.core.extensions.logger
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.client.PbClient
import io.provenance.pbc.clients.CosmosRemoteInvocationException
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.sql.Connection
import javax.sql.DataSource

@Configuration
class DataConfig {

    protected val logger = logger(DataConfig::class)

    @Bean("databaseConnect")
    fun dataConnect(dataSource: DataSource): Database =
            Database.connect(dataSource).also { TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED }

    @Bean
    fun flyway(dataSource: DataSource): Flyway {
        val flyway = Flyway(FluentConfiguration().dataSource(dataSource))
        return flyway
    }

    @Bean
    fun flywayInitializer(flyway: Flyway): FlywayMigrationInitializer = FlywayMigrationInitializer(flyway)

    @Bean
    @Profile("!test")
    fun flywayMigration(dataSource: DataSource, flyway: Flyway): Int {
        flyway.info().all().forEach { logger.info("Flyway migration: ${it.script}") }
        return flyway.migrate()
    }

    @Bean("flywayMigration")
    @Profile("test")
    fun flywayMigrationTest(dataSource: DataSource, flyway: Flyway): Int {
        flyway.info().all().forEach { logger.info("Flyway migration: ${it.script}") }
        flyway.clean()
        return flyway.migrate()
    }

}