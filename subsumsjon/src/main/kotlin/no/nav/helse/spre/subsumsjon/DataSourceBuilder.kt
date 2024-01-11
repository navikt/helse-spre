package no.nav.helse.spre.subsumsjon

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.time.Duration

internal class DataSourceBuilder(private val jdbcDatabaseUrl: String, private val username: String, private val password: String) {


    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = jdbcDatabaseUrl
        username = this@DataSourceBuilder.username
        password = this@DataSourceBuilder.password
        maximumPoolSize = 4
        initializationFailTimeout = Duration.ofMinutes(15).toMillis()
    }

    internal fun migrate() {
        HikariDataSource(hikariConfig).use {
            Flyway.configure()
                .dataSource(it)
                .load()
                .migrate()
        }
    }

    internal fun datasource() = HikariDataSource(hikariConfig)

}
