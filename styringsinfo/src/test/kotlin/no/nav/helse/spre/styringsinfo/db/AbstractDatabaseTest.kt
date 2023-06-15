package no.nav.helse.spre.styringsinfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer

abstract class AbstractDatabaseTest {

    companion object {
        private val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withReuse(true)
            withLabel("app-navn", "spre-styringsinfo")
            start()
            println("🎩 Databasen er startet opp, portnummer: $firstMappedPort, jdbcUrl: jdbc:postgresql://localhost:$firstMappedPort/test, credentials: test og test")
        }

        val dataSource =
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                maximumPoolSize = 5
                minimumIdle = 1
                idleTimeout = 500001
                connectionTimeout = 10000
                maxLifetime = 600001
                initializationFailTimeout = 5000
            })

        init {
            Flyway.configure()
                .dataSource(dataSource)
                .failOnMissingLocations(true)
                .load()
                .migrate()
        }
    }
}

