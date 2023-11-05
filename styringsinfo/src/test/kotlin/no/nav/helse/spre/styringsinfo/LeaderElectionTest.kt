package no.nav.helse.spre.styringsinfo

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LeaderElectionTest {

    @Test
    fun `Pod er leder`() {
        val leaderElection = LeaderElection(
            httpClient = lagMockHttpClient(),
            hostname = "leader-podname",
        )

        runBlocking {
            assertTrue(leaderElection.isLeader())
        }
    }

    @Test
    fun `Pod er ikke leder`() {
        val leaderElection = LeaderElection(
            httpClient = lagMockHttpClient(),
            hostname = "other-podname",
        )

        runBlocking {
            assertFalse(leaderElection.isLeader())
        }
    }

    @Test
    fun `Det kastes exception når kall til elector timer ut`() {
        val httpClient = HttpClient(MockEngine) {
            install(HttpTimeout) {
                requestTimeoutMillis = 100
            }
            engine {
                addHandler {
                    delay(200)
                    lagLeaderElectionResponse()
                }
            }
        }

        val leaderElection = LeaderElection(
            httpClient = httpClient,
            hostname = "not-leader",
        )

        assertThrows(HttpRequestTimeoutException::class.java) {
            runBlocking {
                leaderElection.isLeader()
            }
        }
    }

    @Test
    fun `Retires må gjøres innenfor timeout scope`() {
        val maxAntallRetries = 2
        var antallRetries = 0

        val httpClient = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(objectMapper))
            }
            install(HttpTimeout) {
                // Hvis den ikke settes høyt nok, vil det kastes HttpRequestTimeoutException før alle retries er gjort.
                // https://youtrack.jetbrains.com/issue/KTOR-4652/Retry-and-timeout-client-plugins-dont-work-together
                // https://youtrack.jetbrains.com/issue/KTOR-5466/Connect-timeout-is-not-respected-when-using-the-HttpRequestRetry-plugin
                requestTimeoutMillis = 2000
            }
            install(HttpRequestRetry) {
                // Burde, i motsettning til retryOnServerErrors(), fange opp HttpRequestTimeoutException.
                retryOnExceptionOrServerErrors(maxRetries = maxAntallRetries)
                constantDelay(50)
            }
            engine {
                addHandler {
                    if (antallRetries < maxAntallRetries) {
                        antallRetries++
                        respondError(HttpStatusCode.ServiceUnavailable)
                    } else {
                        lagLeaderElectionResponse()
                    }
                }
            }
        }

        val leaderElection = LeaderElection(
            httpClient = httpClient,
            hostname = "leader-podname",
        )

        runBlocking {
            val isLeader = leaderElection.isLeader()
            assertEquals(maxAntallRetries, antallRetries)
            assertEquals(true, isLeader)
        }
    }

    private fun lagMockHttpClient(): HttpClient {
        return HttpClient(MockEngine) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(objectMapper))
            }
            engine {
                addHandler {
                    lagLeaderElectionResponse()
                }
            }
        }
    }

    private fun MockRequestHandleScope.lagLeaderElectionResponse() = respond(
        content = "{\"name\":\"leader-podname\",\"last_update\":\"2023-10-10T10:00:00Z\"}".toByteArray(),
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )
}