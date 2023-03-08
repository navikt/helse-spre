package no.nav.helse.spre.oppgaver

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.Duration
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

internal interface DataSourceProvider {
    fun datasource(): DataSource
    fun migrate()
}

internal class DataSourceBuilderGCP : DataSourceProvider {

    private companion object {
        private val logger = LoggerFactory.getLogger(DataSourceBuilderGCP::class.java)
    }

    private val databaseHost: String = requireNotNull(System.getenv("DATABASE_HOST")) { "host må settes" }
    private val databasePort: String = requireNotNull(System.getenv("DATABASE_PORT")) { "port må settes" }
    private val databaseName: String = requireNotNull(System.getenv("DATABASE_DATABASE")) { "databasenavn må settes" }
    private val databaseUsername: String = requireNotNull(System.getenv("DATABASE_USERNAME")) { "brukernavn må settes" }
    private val databasePassword: String = requireNotNull(System.getenv("DATABASE_PASSWORD")) { "passord må settes" }

    private val dbUrl = String.format("jdbc:postgresql://%s:%s/%s", databaseHost, databasePort, databaseName)

    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = dbUrl
        username = databaseUsername
        password = databasePassword
        connectionTimeout = Duration.ofSeconds(30).toMillis()
        initializationFailTimeout = Duration.ofMinutes(30).toMillis()
        maximumPoolSize = 1
    }

    private val dataSource by lazy { HikariDataSource(hikariConfig) }

    override fun datasource() = dataSource

    override fun migrate() {
        logger.info("Migrerer database")
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = dbUrl
            username = databaseUsername
            password = databasePassword
            connectionTimeout = Duration.ofSeconds(30).toMillis()
            initializationFailTimeout = Duration.ofMinutes(30).toMillis()
            maximumPoolSize = 2
        }).use { migrateDataSource ->
            Flyway.configure()
                .dataSource(migrateDataSource)
                .lockRetryCount(-1)
                .load()
                .migrate()
        }
        logger.info("Migrering ferdig!")
    }
}