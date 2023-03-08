package no.nav.helse.spre.oppgaver

import com.zaxxer.hikari.HikariConfig
import org.flywaydb.core.Flyway
import java.util.*
import javax.sql.DataSource
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration as createDataSource

internal class DataSourceBuilder(env: Map<String, String> = System.getenv()) : DataSourceProvider {
    private val databaseName =
        requireNotNull(env["DATABASE_NAME"]) { "database name must be set if jdbc url is not provided" }
    private val databaseHost =
        requireNotNull(env["DATABASE_HOST"]) { "database host must be set if jdbc url is not provided" }
    private val databasePort =
        requireNotNull(env["DATABASE_PORT"]) { "database port must be set if jdbc url is not provided" }
    private val vaultMountPath = env["VAULT_MOUNTPATH"]

    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = env["DATABASE_JDBC_URL"] ?: String.format(
            "jdbc:postgresql://%s:%s/%s", databaseHost, databasePort,
            databaseName
        )

        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
        initializationFailTimeout = 30000
    }

    override fun datasource() = datasource(Role.User)

    private fun datasource(role: Role) =
        createDataSource(hikariConfig, vaultMountPath, role.asRole(databaseName))

    override fun migrate() {
        runMigration(datasource(Role.Admin), "SET ROLE \"${Role.Admin.asRole(databaseName)}\"")
    }

    private fun runMigration(dataSource: DataSource, initSql: String? = null) =
        Flyway.configure()
            .dataSource(dataSource)
            .initSql(initSql)
            .load()
            .migrate()

    enum class Role {
        Admin, User;

        fun asRole(databaseName: String) = "$databaseName-${name.lowercase(Locale.getDefault())}"
    }
}
