package no.nav.helse.spre.saksbehandlingsstatistikk

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

internal class DataSourceBuilder(private val dbConfig: Environment.DB) {

    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = dbConfig.jdbcUrl
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }

    fun getMigratedDataSource(): HikariDataSource = HikariDataSource(hikariConfig).apply {
        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
    }

}
