package no.nav.helse.spre.subsumsjon

class Config(private val env: Map<String, String>) {
    internal val databaseName =
        requireNotNull(env["DATABASE_NAME"]) { "database name must be set if jdbc url is not provided" }
    internal val databaseHost =
        requireNotNull(env["DATABASE_HOST"]) { "database host must be set if jdbc url is not provided" }
    internal val databasePort =
        requireNotNull(env["DATABASE_PORT"]) { "database port must be set if jdbc url is not provided" }

    companion object {
        fun fromEnv() = Config(System.getenv())
    }
}