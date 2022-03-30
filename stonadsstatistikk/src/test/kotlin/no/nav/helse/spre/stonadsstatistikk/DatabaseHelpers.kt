package no.nav.helse.spre.stonadsstatistikk

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.testcontainers.containers.PostgreSQLContainer

class DatabaseHelpers {
    companion object {
        private val postgres = PostgreSQLContainer<Nothing>("postgres:13").also { it.start() }
        val dataSource: DataSource =
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                maximumPoolSize = 3
                minimumIdle = 1
                idleTimeout = 10001
                connectionTimeout = 1000
                maxLifetime = 30001
                initializationFailTimeout = 10000
            })
    }
}

