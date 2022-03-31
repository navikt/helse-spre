package no.nav.helse.spre.saksbehandlingsstatistikk

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

internal class DataSourceBuilder(private val dbConfig: Environment.DB) {

    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = dbConfig.jdbcUrl
        username = dbConfig.username
        password = dbConfig.password
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
        initializationFailTimeout = 30000
    }

    fun getMigratedDataSource(): HikariDataSource = HikariDataSource(hikariConfig).apply {
        Flyway.configure()
            .dataSource(this)
            .load()
            .migrate()
    }

}
