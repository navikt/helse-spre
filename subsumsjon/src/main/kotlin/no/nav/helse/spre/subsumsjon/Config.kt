package no.nav.helse.spre.subsumsjon

class Config(
    private val databaseName: String,
    private val databaseHost: String,
    private val databasePort: String,
    internal val username: String,
    internal val password: String
) {


    val jdbcUrl: String get() = "jdbc:postgresql://${databaseHost}:${databasePort}/${databaseName}"

    companion object {

        fun fromEnv(): Config {
            val env = System.getenv()
            val databaseName =
                requireNotNull(env["DB_NAME"]) { "database name must be set if jdbc url is not provided" }
            val databaseHost =
                requireNotNull(env["DB_HOST"]) { "database host must be set if jdbc url is not provided" }
            val databasePort =
                requireNotNull(env["DB_PORT"]) { "database port must be set if jdbc url is not provided" }
            val username =
                requireNotNull(env["DB_USERNAME"]) { "database port must be set if jdbc url is not provided" }
            val password =
                requireNotNull(env["DB_PASSWORD"]) { "database port must be set if jdbc url is not provided" }
            return Config(databaseName, databaseHost, databasePort, username, password)
        }
    }
}