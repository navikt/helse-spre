package no.nav.helse.spre.styringsinfo

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.jackson.*

private const val HTTP_PREFIX = "http://"
private const val DEFAULT_ELECTOR_PATH = "localhost:"

class LeaderElection(
    private val httpClient: HttpClient,
    private val hostname: String,
    environment: Map<String, String> = mapOf("ELECTOR_PATH" to DEFAULT_ELECTOR_PATH)
) {
    private val electorPath: String = requireNotNull(environment["ELECTOR_PATH"]) { "ELECTOR_PATH er ikke satt." }

    private data class Leader(val name: String)

    suspend fun isLeader(): Boolean {
        buildUrl(electorPath).also {
            val leader: Leader = httpClient.get(it).body()
            return leader.name == hostname
        }
    }

    private fun buildUrl(url: String): String = if (url.startsWith(HTTP_PREFIX)) url else "$HTTP_PREFIX$url"
}
