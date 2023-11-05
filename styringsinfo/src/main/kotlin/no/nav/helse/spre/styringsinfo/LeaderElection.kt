package no.nav.helse.spre.styringsinfo

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import java.net.InetAddress

private const val HTTP_PREFIX = "http://"
private const val DEFAULT_ELECTOR_PATH = "localhost:"

class LeaderElection(
    private val httpClient: HttpClient,
    private val hostname: String,
    environment: Map<String, String> = mapOf("ELECTOR_PATH" to DEFAULT_ELECTOR_PATH)
) {
    private val electorPath: String = requireNotNull(environment["ELECTOR_PATH"]) { "ELECTOR_PATH er ikke satt." }

    companion object {

        fun build(
            environment: MutableMap<String, String>,
            connectTimeout: Long = 1_000,
            requestTimeout: Long = 1_000
        ): LeaderElection {
            val hostname: String = InetAddress.getLocalHost().hostName
            val httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    register(ContentType.Application.Json, JacksonConverter(objectMapper))
                }
                install(HttpTimeout) {
                    connectTimeoutMillis = connectTimeout
                    requestTimeoutMillis = requestTimeout
                }
            }
            return LeaderElection(httpClient, hostname, environment)
        }
    }

    suspend fun isLeader(): Boolean {
        buildUrl(electorPath).also {
            val leader: Leader = httpClient.get(it).body()
            return leader.name == hostname
        }
    }

    private fun buildUrl(url: String): String = if (url.startsWith(HTTP_PREFIX)) url else "$HTTP_PREFIX$url"

    private data class Leader(val name: String)
}


