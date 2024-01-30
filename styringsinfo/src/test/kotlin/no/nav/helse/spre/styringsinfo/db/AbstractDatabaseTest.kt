package no.nav.helse.spre.styringsinfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractDatabaseTest {

    @BeforeAll
    fun truncateAllTheTings() {
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    """truncate sendt_soknad; truncate vedtak_fattet; truncate vedtak_dokument_mapping;"""
                ).asUpdate)
        }
    }

    companion object {
        private val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            // Cloud SQL har wal_level = 'logical' på grunn av flagget cloudsql.logical_decoding i
            // naiserator.yaml. Vi må sette det samme lokalt for at flyway migrering skal fungere.
            withCommand("postgres", "-c", "wal_level=logical")
            withReuse(true)
            withLabel("app-navn", "spre-styringsinfo")
            start()
            println("🎩 Databasen er startet opp, portnummer: $firstMappedPort, jdbcUrl: jdbc:postgresql://localhost:$firstMappedPort/test, credentials: test og test")
            println("Database: jdbc:postgresql://localhost:$firstMappedPort/test, credentials: test og test")
        }

        val dataSource =
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                maximumPoolSize = 5
                initializationFailTimeout = Duration.ofMinutes(15).toMillis()
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

