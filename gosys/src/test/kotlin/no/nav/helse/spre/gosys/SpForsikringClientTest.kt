package no.nav.helse.spre.gosys

import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.ok
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SpForsikringClientTest {

    private lateinit var server: WireMockServer
    private lateinit var client: SpForsikringClient

    private val azureMock: AzureTokenProvider = mockk {
        every { bearerToken("scope") } returns AzureToken("token", LocalDateTime.MAX).ok()
    }

    @BeforeEach
    fun setup() {
        server = WireMockServer(WireMockConfiguration.options().dynamicPort()).also {
            it.start()
            WireMock.configureFor(it.port())
        }
        client = SpForsikringClient(
            baseUrl = "http://localhost:${server.port()}",
            azureClient = azureMock,
            scope = "scope",
            httpClient = httpClient(),
        )
    }

    @AfterEach
    fun teardown() {
        server.stop()
    }

    @Test
    fun `mapper individuell forsikring som er lagt til grunn og kollektiv forsikring`() = runBlocking {
        val id = UUID.randomUUID()
        stubForsikringsvurdering(
            id, 200, """
            {
              "navKjøpteForsikringer": [
                { "navn": "Ikke valgt forsikring", "lagtTilGrunn": false },
                { "navn": "Valgt individuell forsikring", "lagtTilGrunn": true }
              ],
              "kollektivForsikring": { "navn": "Kollektiv forsikring" },
              "samletDekning": { "grad": 100, "fraDag": 1 }
            }
        """.trimIndent()
        )

        val forsikringsvurdering = client.hentForsikringsvurdering(id)

        assertEquals("Valgt individuell forsikring", forsikringsvurdering?.indivieduellForsikringNavn)
        assertEquals("Kollektiv forsikring", forsikringsvurdering?.kollektivForsikringNavn)
        assertEquals(100, forsikringsvurdering?.dekning?.dekningsgrad)
        assertEquals(1, forsikringsvurdering?.dekning?.gjelderFraDag)
    }

    @Test
    fun `individuell forsikringsnavn blir null når ingen er lagt til grunn`() = runBlocking {
        val id = UUID.randomUUID()
        stubForsikringsvurdering(
            id, 200, """
            {
              "navKjøpteForsikringer": [
                { "navn": "Ikke valgt forsikring", "lagtTilGrunn": false }
              ],
              "kollektivForsikring": { "navn": "Kollektiv forsikring" },
              "samletDekning": { "grad": 100, "fraDag": 1 }
            }
        """.trimIndent()
        )

        val forsikringsvurdering = client.hentForsikringsvurdering(id)

        assertNull(forsikringsvurdering?.indivieduellForsikringNavn)
        assertEquals("Kollektiv forsikring", forsikringsvurdering?.kollektivForsikringNavn)
    }

    @Test
    fun `dekning blir null når samletDekning er null`() = runBlocking {
        val id = UUID.randomUUID()
        stubForsikringsvurdering(
            id, 200, """
            {
              "navKjøpteForsikringer": [],
              "kollektivForsikring": { "navn": "Kollektiv forsikring" },
              "samletDekning": null
            }
        """.trimIndent()
        )

        val forsikringsvurdering = client.hentForsikringsvurdering(id)

        assertNull(forsikringsvurdering?.dekning)
    }

    @Test
    fun `returnerer null ved 404`() = runBlocking {
        val id = UUID.randomUUID()
        stubForsikringsvurdering(id, 404, "")

        assertNull(client.hentForsikringsvurdering(id))
    }

    @Test
    fun `kaster feil ved 500`() = runBlocking {
        val id = UUID.randomUUID()
        stubForsikringsvurdering(id, 500, "")

        val exception = assertThrows<IllegalStateException> {
            runBlocking { client.hentForsikringsvurdering(id) }
        }
        assertEquals("Feil fra sp-forsikring: 500", exception.message)
    }

    private fun stubForsikringsvurdering(id: UUID, status: Int, body: String) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("/forsikringsvurderinger/$id"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )
    }

    private fun httpClient() = HttpClient {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter(objectMapper))
        }
        install(HttpTimeout) { requestTimeoutMillis = 10000 }
    }
}
