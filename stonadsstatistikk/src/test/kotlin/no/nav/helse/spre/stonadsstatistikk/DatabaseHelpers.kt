package no.nav.helse.spre.stonadsstatistikk

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

class DatabaseHelpers {
    companion object {
        val dataSource: DataSource = dataSource()
        private fun dataSource(): DataSource {
            val embeddedPostgres = EmbeddedPostgres.builder().setPort(56789).start()
            val hikariConfig = HikariConfig().apply {
                this.jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres")
                maximumPoolSize = 3
                minimumIdle = 1
                idleTimeout = 10001
                connectionTimeout = 1000
                maxLifetime = 30001
            }
            return HikariDataSource(hikariConfig)
                .apply {
                    Flyway
                        .configure()
                        .dataSource(this)
                        .load().also(Flyway::migrate)
                }
        }
    }

}
