package no.nav.helse.spre.subsumsjon

class Config(
    private val databaseName: String,
    private val databaseHost: String,
    private val databasePort: String,
    internal val username: String,
    internal val password: String,
    internal val subsumsjonTopic: String
) {


    val jdbcUrl: String get() = "jdbc:postgresql://${databaseHost}:${databasePort}/${databaseName}"

    companion object {

        fun fromEnv(): Config {
            val env = System.getenv()
            val databaseName =
                requireNotNull(env["DB_DATABASE"]) { "DB_DATABASE is required config" }
            val databaseHost =
                requireNotNull(env["DB_HOST"]) { "DB_HOST is required config" }
            val databasePort =
                requireNotNull(env["DB_PORT"]) { "DB_PORT is required config" }
            val username =
                requireNotNull(env["DB_USERNAME"]) { "DB_USERNAME is required config" }
            val password =
                requireNotNull(env["DB_PASSWORD"]) { "DB_PASSWORD is required config" }
            val topic = requireNotNull(env["SUBSUMSJON_TOPIC"]) { " SUBSUMSJON_TOPIC is required config " }
            return Config(databaseName, databaseHost, databasePort, username, password, topic)
        }
    }
}