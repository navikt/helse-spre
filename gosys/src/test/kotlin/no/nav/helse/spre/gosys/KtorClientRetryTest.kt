package no.nav.helse.spre.gosys

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.matching.UrlPattern
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class KtorClientRetryTest {

    @Test
    fun `Execute retry fungerer som en sku tru når mappingen thrower`() = runBlocking {
        val server = server(WireMock.aResponse().withStatus(200))
        val client = client()
        val url = "http://localhost:${server.port()}"

        val response = client.prepareGet(url).executeRetry { response ->
            val tall = response.bodyAsText().toInt()
            if (tall <= 3) throw IllegalStateException("Bø")
            tall
        }

        assertEquals(4, response)

        assertThrows<IllegalStateException> {
            client.prepareGet(url).executeRetry { _ -> throw IllegalStateException("Bø") }
        }

        server.stop()
    }

    @Test
    fun `Execute retry fungerer som en sku tru når kallet feiler`() = runBlocking {
        val server = server(WireMock.serverError(), WireMock.aResponse().withStatus(200))
        val client = client()
        val url = "http://localhost:${server.port()}"

        val response = client.prepareGet(url) { expectSuccess = true }.executeRetry { it }
            assertEquals(HttpStatusCode.OK, response.status)


        assertEquals(2, response.bodyAsText().toInt())

        server.stop()
    }

    @Test
    fun `Execute retry prøver fire ganger og kaster deretter opp`() = runBlocking {
        val server = server(WireMock.serverError())
        val client = client()
        val url = "http://localhost:${server.port()}"

        val exception = assertThrows<ServerResponseException> {
            client.prepareGet(url) { expectSuccess = true }.executeRetry { response ->
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                response.bodyAsText().toInt()
            }
        }

        val forventetSvar = """Text: "4""""
        assertTrue(exception.message.contains(forventetSvar)) { "${exception.message} inneholdt ikke $forventetSvar" }

        server.stop()
    }

    private companion object {
        private fun client() = HttpClient {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(objectMapper))
            }
            install(HttpTimeout) { requestTimeoutMillis = 10000 }
        }

        private fun server(response: ResponseDefinitionBuilder, response2: ResponseDefinitionBuilder? = null) =
            WireMockServer(WireMockConfiguration.options().dynamicPort().extensions(TellerTransformer())).also {
                it.start()
                WireMock.configureFor(it.port())
                WireMock.stubFor(WireMock.get(UrlPattern.ANY).inScenario("et scenario").willReturn(response).willSetStateTo("klar for 2. respons"))
                if (response2 != null)
                    WireMock.stubFor(WireMock.get(UrlPattern.ANY).inScenario("et scenario").whenScenarioStateIs("klar for 2. respons").willReturn(response2))
            }

        private class TellerTransformer : ResponseTransformerV2 {
            private var teller = 0
            override fun getName() = "TellerTransformer"
            override fun applyGlobally() = true
            override fun transform(response: Response?, serveEvent: ServeEvent?): Response =
                Response.Builder.like(response).status(response?.status ?: 503).body("${++teller}").build()
        }
    }
}
