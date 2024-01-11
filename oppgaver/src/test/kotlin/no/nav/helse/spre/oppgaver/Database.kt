package no.nav.helse.spre.oppgaver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Duration

internal fun setupDataSourceMedFlyway(): DataSource {
    val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
        withLabel("app-navn", "spre-oppgaver")
        withReuse(true)
        start()
    }
    val dataSource: DataSource =
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            initializationFailTimeout = Duration.ofMinutes(15).toMillis()
        })

    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()

    return dataSource
}
