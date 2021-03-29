package no.nav.helse.spre.gosys

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64

const val vaultBase = "/var/run/secrets/nais.io/service_user"
val vaultBasePath: Path = Paths.get(vaultBase)

fun readServiceUserCredentials() = ServiceUser(
    username = Files.readString(vaultBasePath.resolve("username")),
    password = Files.readString(vaultBasePath.resolve("password"))
)

fun readDatabaseEnvironment() = DatabaseEnvironment(
    databaseName = System.getenv("DATABASE_NAME"),
    databaseHost = System.getenv("DATABASE_HOST"),
    databasePort = System.getenv("DATABASE_PORT"),
    vaultMountPath = System.getenv("VAULT_MOUNTPATH")
)

data class ServiceUser(
    val username: String,
    val password: String
) {
    val basicAuth = "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}"
}

class DatabaseEnvironment(
    val databaseName: String,
    val vaultMountPath: String,
    databaseHost: String,
    databasePort: String
) {
    val jdbcUrl = "jdbc:postgresql://$databaseHost:$databasePort/$databaseName"
}