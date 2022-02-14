plugins {
    id(PluginIds.Flyway) version PluginVersions.Flyway
}

dependencies {
    implementation(Libraries.FlywayCore)
}

flyway {
    url = "jdbc:postgresql://127.0.0.1:5432/explorer"
    driver = "org.postgresql.Driver"
    user = "postgres"
    password = "password1"
    schemas = arrayOf("explorer")
    locations = arrayOf("filesystem:$projectDir/src/main/resources/db/migration")
    validateOnMigrate = false
    outOfOrder = false
}
