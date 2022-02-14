package no.nav.helse.spre.subsumsjon.no.nav.helse.spre.subsumsjon

class Config(private val databaseName: String, private val databaseHost: String, private val databasePort: String) {


    val jdbcUrl: String get() = "jdbc:postgresql://${databaseHost}:${databasePort}/${databaseName}"

    companion object {

        fun fromEnv() {
            val env = System.getenv()
            val databaseName =
                requireNotNull(env["DATABASE_NAME"]) { "database name must be set if jdbc url is not provided" }
            val databaseHost =
                requireNotNull(env["DATABASE_HOST"]) { "database host must be set if jdbc url is not provided" }
            val databasePort =
                requireNotNull(env["DATABASE_PORT"]) { "database port must be set if jdbc url is not provided" }
            Config(databaseName, databaseHost, databasePort)
        }
    }
}