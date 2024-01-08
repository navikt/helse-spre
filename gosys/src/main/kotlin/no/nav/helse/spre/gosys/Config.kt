package no.nav.helse.spre.gosys

fun readDatabaseEnvironment() = DatabaseEnvironment(
    databaseName = System.getenv("DATABASE_NAME"),
    databaseHost = System.getenv("DATABASE_HOST"),
    databasePort = System.getenv("DATABASE_PORT"),
    vaultMountPath = System.getenv("VAULT_MOUNTPATH")
)

class DatabaseEnvironment(
    val databaseName: String,
    val vaultMountPath: String,
    databaseHost: String,
    databasePort: String
) {
    val jdbcUrl = "jdbc:postgresql://$databaseHost:$databasePort/$databaseName"
}