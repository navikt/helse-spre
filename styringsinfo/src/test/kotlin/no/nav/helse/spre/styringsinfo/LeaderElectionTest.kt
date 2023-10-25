package no.nav.helse.spre.styringsinfo

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LeaderElectionTest {

    private val mockHttpClient = createMockHttpClient()

    private fun createMockHttpClient(): HttpClient {
        return HttpClient(MockEngine) {
            install(ContentNegotiation) {
                jackson {
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 500
            }
            engine {
                addHandler {
                    respond(
                        content = "{\"name\":\"leader-podname\",\"last_update\":\"2023-10-10T10:00:00Z\"}".toByteArray(),
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                    )
                }
            }
        }
    }

    @Test
    fun `Pod er leder`() {
        val leaderElection = LeaderElection(
            httpClient = mockHttpClient,
            hostname = "leader-podname",
        )

        runBlocking {
            assertTrue(leaderElection.isLeader())
        }
    }

    @Test
    fun `Pod er ikke leder`() {
        val leaderElection = LeaderElection(
            httpClient = mockHttpClient,
            hostname = "other-podname",
        )

        runBlocking {
            assertFalse(leaderElection.isLeader())
        }
    }
}