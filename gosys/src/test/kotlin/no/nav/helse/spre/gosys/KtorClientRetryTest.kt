package no.nav.helse.spre.gosys

import com.github.tomakehurst.wiremock.WireMockServer
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class KtorClientRetryTest {

    @Test
    fun `Execute retry fungerer som en sku tru`() = runBlocking {
        val server = server()
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

    private companion object {
        private fun client() = HttpClient {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(objectMapper))
            }
            install(HttpTimeout) { requestTimeoutMillis = 10000 }
        }

        private fun server() =
            WireMockServer(WireMockConfiguration.options().dynamicPort().extensions(TellerTransformer)).also {
                it.start()
                WireMock.configureFor(it.port())
                WireMock.stubFor(WireMock.get(UrlPattern.ANY).willReturn(WireMock.aResponse().withStatus(200).withTransformers("TellerTransformer")))
            }

        private object TellerTransformer: ResponseTransformerV2 {
            private var teller = 0
            override fun getName() = "TellerTransformer"
            override fun applyGlobally() = true
            override fun transform(response: Response?, serveEvent: ServeEvent?) = Response.Builder.like(response).status(200).body("${++teller}").build()
        }
    }
}