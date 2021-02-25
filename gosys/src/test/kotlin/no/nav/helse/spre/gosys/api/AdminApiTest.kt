package no.nav.helse.spre.gosys.api

import com.fasterxml.jackson.core.util.ByteArrayBuilder
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.helse.spre.gosys.JoarkClient
import no.nav.helse.spre.gosys.PdfClient
import no.nav.helse.spre.gosys.io.IO
import no.nav.helse.spre.gosys.io.mockUtbetalinger
import no.nav.helse.spre.gosys.vedtak.VedtakMediator
import no.nav.helse.spre.gosys.vedtak.VedtakMessage
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload
import no.nav.helse.spre.gosys.wiring
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals

class AdminApiTest {
    private val apiSecret = "hunter2"
    private val wrongApiSecret = "\uD83C\uDD71️"
    private val envMedApiSecret = mapOf("ADMIN_SECRET" to "hunter2")

    @Test
    fun `ende til ende`() {
        val format = Json { ignoreUnknownKeys = true }
        val pdfClient: PdfClient = mockk(relaxed = true)
        val joarkClient: JoarkClient = mockk(relaxed = true)
        val vedtakMediator = VedtakMediator(pdfClient, joarkClient)
        val slot = mutableListOf<VedtakPdfPayload>()


        coEvery { pdfClient.hentVedtakPdf(capture(slot)) } returns ""
        coEvery { joarkClient.opprettJournalpost(any(), any()) } returns true

        withTestApplication({ wiring(envMedApiSecret, vedtakMediator) }) {
            with(kallUtbetalinger(apiSecret)) {
                assertEquals(HttpStatusCode.OK, response.status())

                coVerify(exactly = mockUtbetalinger.size) {
                    joarkClient.opprettJournalpost(any(), any())
                    pdfClient.hentVedtakPdf(any())
                }

                mockUtbetalinger.forEachIndexed { index, it ->
                    val vedtak: IO.Vedtak = format.decodeFromString(it)
                    assertEquals(slot[index].fagsystemId, vedtak.utbetalt[0].fagsystemId)
                    assertEquals(
                        slot[index].linjer.getOrNull(0)?.fom,
                        vedtak.utbetalt.getOrNull(0)?.utbetalingslinjer?.getOrNull(0)?.fom
                    )
                }
            }
        }
    }

    private fun TestApplicationEngine.kallUtbetalinger(apiSecret: String): TestApplicationCall {
        return handleRequest(HttpMethod.Post, "/admin/utbetaling") {
            val userpass = Base64.getEncoder().encodeToString("admin:$apiSecret".toByteArray())
            addHeader(HttpHeaders.Authorization, "Basic $userpass")
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody(vedtakAsByteArray(mockUtbetalinger))
        }
    }

    @Test
    fun `oppslag på utbetaling`() {
        val mediator: VedtakMediator = mockk(relaxed = true)
        withTestApplication({ wiring(envMedApiSecret, mediator) }) {
            with(kallUtbetalinger(apiSecret)) {
                assertEquals(HttpStatusCode.OK, response.status())

                val format = Json { ignoreUnknownKeys = true }
                val vedtak: List<IO.Vedtak> = mockUtbetalinger.map(format::decodeFromString)
                verify(exactly = 1) {
                    mediator.opprettVedtak(VedtakMessage(vedtak[0]))
                }
            }
        }
    }

    @Test
    fun `unaut️horized uten autentisering`() {
        val mediator: VedtakMediator = mockk(relaxed = true)
        withTestApplication({
            wiring(envMedApiSecret, mediator)
        }) {
            with(kallUtbetalinger(wrongApiSecret)) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
                verify(exactly = 0) { mediator.opprettVedtak(any()) }
            }
        }
    }


    private fun vedtakAsByteArray(list: List<String>): ByteArray {
        val byteArray = ByteArrayBuilder()
        byteArray.write("[".toByteArray())
        list.forEachIndexed { index, it ->
            byteArray.write(it.toByteArray())
            if (index != list.size - 1) {
                byteArray.write(",".toByteArray())
            }
        }
        byteArray.write("]".toByteArray())
        return byteArray.toByteArray()
    }
}
