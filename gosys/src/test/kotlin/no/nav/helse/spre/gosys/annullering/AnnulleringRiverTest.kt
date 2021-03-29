package no.nav.helse.spre.gosys.annullering

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.*
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.gosys.JoarkClient
import no.nav.helse.spre.gosys.JournalpostPayload
import no.nav.helse.spre.gosys.PdfClient
import no.nav.helse.spre.gosys.StsRestClient
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class AnnulleringRiverTest {
    private val testRapid = TestRapid()
    private val stsMock: StsRestClient = mockk {
        coEvery { token() }.returns("6B70C162-8AAB-4B56-944D-7F092423FE4B")
    }
    private val mockClient = httpclient()
    private val joarkClient = spyk(JoarkClient("https://url.no", stsMock, mockClient))
    private val pdfClient = PdfClient(mockClient)
    val dataSource = setupDataSourceMedFlyway()
    val duplikatsjekkDao = DuplikatsjekkDao(dataSource)
    private val annulleringMediator = AnnulleringMediator(pdfClient, joarkClient, duplikatsjekkDao)

    private var capturedJoarkRequest: HttpRequestData? = null
    private var capturedPdfRequest: HttpRequestData? = null

    init {
        AnnulleringRiver(testRapid, annulleringMediator)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @KtorExperimentalAPI
    @Test
    fun `journalfører en annullering`() {
        runBlocking {
            val hendelseId = UUID.randomUUID()
            testRapid.sendTestMessage(annullering(hendelseId))
            val joarkRequest = requireNotNull(capturedJoarkRequest)
            val joarkPayload =
                requireNotNull(objectMapper.readValue(joarkRequest.body.toByteArray(), JournalpostPayload::class.java))

            assertEquals("Bearer 6B70C162-8AAB-4B56-944D-7F092423FE4B", joarkRequest.headers["Authorization"])
            assertEquals(hendelseId.toString(), joarkRequest.headers["Nav-Consumer-Token"])
            assertEquals("application/json", joarkRequest.body.contentType.toString())
            assertEquals(expectedJournalpost(), joarkPayload)

            val pdfRequest = requireNotNull(capturedPdfRequest)
            val pdfPayload =
                requireNotNull(objectMapper.readValue(pdfRequest.body.toByteArray(), AnnulleringPdfPayload::class.java))

            val expectedPdfPayload = AnnulleringPdfPayload(
                fødselsnummer = "fnr",
                fagsystemId = "77ATRH3QENHB5K4XUY4LQ7HRTY",
                fom = LocalDate.of(2020, 1, 1),
                tom = LocalDate.of(2020, 1, 10),
                organisasjonsnummer = "orgnummer",
                dato = LocalDateTime.of(2020, 5, 4, 8, 8, 0),
                saksbehandlerId = "sara.saksbehandler@nav.no",
                linjer = listOf(
                    AnnulleringPdfPayload.Linje(
                        fom = LocalDate.of(2020, 1, 1),
                        tom = LocalDate.of(2020, 1, 10),
                        grad = 100,
                        beløp = 1345
                    )
                )
            )

            assertEquals(expectedPdfPayload, pdfPayload)
        }
    }

    @Test
    fun `oppretter kun en journalpost ved duplikat`() {
        val annullering = annullering()
        testRapid.sendTestMessage(annullering)
        testRapid.sendTestMessage(annullering)

        coVerify(exactly = 1) {
            joarkClient.opprettJournalpost(any(), any())
        }
    }

    private fun httpclient(): HttpClient {
        return HttpClient(MockEngine) {
            install(JsonFeature) {
                serializer = JacksonSerializer(objectMapper)
            }
            engine {
                addHandler { request ->
                    when (request.url.fullPath) {
                        "/rest/journalpostapi/v1/journalpost?forsoekFerdigstill=true" -> {
                            capturedJoarkRequest = request
                            respond("Hello, world")
                        }
                        "/api/v1/genpdf/spre-gosys/annullering" -> {
                            capturedPdfRequest = request
                            respond("Test".toByteArray())
                        }
                        else -> error("Unhandled ${request.url.fullPath}")
                    }
                }
            }
        }
    }

    @Language("JSON")
    private fun annullering(id: UUID = UUID.randomUUID()) = """
        {
            "@event_name": "utbetaling_annullert",
            "@opprettet": "2020-05-04T11:26:47.088455",
            "@id": "$id",
            "fødselsnummer": "fnr",
            "aktørId": "aktørid",
            "organisasjonsnummer": "orgnummer",
            "fagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
            "saksbehandlerEpost": "sara.saksbehandler@nav.no",
            "annullertAvSaksbehandler": "2020-05-04T08:08:00.00000",
            "utbetalingslinjer": [
                {
                  "fom": "2020-01-01",
                  "tom": "2020-01-10",
                  "grad": 100,
                  "beløp": 1345
                }
            ]
        }
    """

    private fun expectedJournalpost(): JournalpostPayload {
        return JournalpostPayload(
            tittel = "Annullering av vedtak om sykepenger",
            journalpostType = "NOTAT",
            tema = "SYK",
            behandlingstema = "ab0061",
            journalfoerendeEnhet = "9999",
            bruker = JournalpostPayload.Bruker(
                id = "fnr",
                idType = "FNR"
            ),
            sak = JournalpostPayload.Sak(
                sakstype = "GENERELL_SAK"
            ),
            dokumenter = listOf(
                JournalpostPayload.Dokument(
                    tittel = "Utbetaling annullert i ny løsning 01.01.2020 - 10.01.2020",
                    dokumentvarianter = listOf(
                        JournalpostPayload.Dokument.DokumentVariant(
                            filtype = "PDFA",
                            fysiskDokument = Base64.getEncoder().encodeToString("Test".toByteArray()),
                            variantformat = "ARKIV"
                        )
                    )
                )
            )
        )
    }
}
