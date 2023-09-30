package no.nav.helse.spre.subsumsjon

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

internal class DataSourceBuilder(private val jdbcDatabaseUrl: String, private val username: String, private val password: String) {


    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = jdbcDatabaseUrl
        username = this@DataSourceBuilder.username
        password = this@DataSourceBuilder.password
        maximumPoolSize = 4
        minimumIdle = 2
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
        initializationFailTimeout = 30000
    }

    internal fun migratedDataSource(): HikariDataSource = HikariDataSource(hikariConfig).apply {
        Flyway.configure()
            .dataSource(this)
            .load()
            .migrate()
    }

    internal fun datasource() = HikariDataSource(hikariConfig)

}
