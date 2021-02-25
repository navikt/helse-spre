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

data class ServiceUser(
    val username: String,
    val password: String
) {
    val basicAuth = "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}"
}
