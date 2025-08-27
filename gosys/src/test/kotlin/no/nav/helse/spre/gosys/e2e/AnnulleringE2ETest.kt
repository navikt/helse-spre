package no.nav.helse.spre.gosys.e2e

import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.result_object.ok
import com.github.navikt.tbd_libs.speed.PersonResponse
import com.github.navikt.tbd_libs.speed.SpeedClient
import com.github.navikt.tbd_libs.test_support.TestDataSource
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.EregClient
import no.nav.helse.spre.gosys.JoarkClient
import no.nav.helse.spre.gosys.JournalpostPayload
import no.nav.helse.spre.gosys.PdfClient
import no.nav.helse.spre.gosys.annullering.AnnulleringDao
import no.nav.helse.spre.gosys.annullering.AnnulleringPdfPayload
import no.nav.helse.spre.gosys.annullering.PlanlagtAnnulleringDao
import no.nav.helse.spre.gosys.databaseContainer
import no.nav.helse.spre.gosys.eregResponse
import no.nav.helse.spre.gosys.feriepenger.FeriepengerMediator
import no.nav.helse.spre.gosys.objectMapper
import no.nav.helse.spre.gosys.settOppRivers
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AnnulleringE2ETest {

    @Test
    fun `journalfører en annullering`() {
        runBlocking {
            val hendelseId = UUID.randomUUID()
            val utbetalingId = UUID.randomUUID()
            testRapid.sendTestMessage(annullering(id = hendelseId, utbetalingId = utbetalingId))
            val joarkRequest = capturedJoarkRequests.single()
            val joarkPayload =
                requireNotNull(objectMapper.readValue(joarkRequest.body.toByteArray(), JournalpostPayload::class.java))

            assertEquals("Bearer 6B70C162-8AAB-4B56-944D-7F092423FE4B", joarkRequest.headers["Authorization"])
            assertEquals(hendelseId.toString(), joarkRequest.headers["Nav-Consumer-Token"])
            assertEquals("application/json", joarkRequest.body.contentType.toString())
            assertEquals(expectedJournalpost(utbetalingId), joarkPayload)

            val pdfRequest = capturedPdfRequests.single()
            val pdfPayload =
                requireNotNull(objectMapper.readValue(pdfRequest.body.toByteArray(), AnnulleringPdfPayload::class.java))

            val expectedPdfPayload = AnnulleringPdfPayload(
                fødselsnummer = "fnr",
                fom = LocalDate.of(2020, 1, 1),
                tom = LocalDate.of(2020, 1, 10),
                organisasjonsnummer = "123456789",
                dato = LocalDateTime.of(2020, 5, 4, 8, 8, 0),
                epost = "sara.saksbehandler@nav.no",
                ident = "A123456",
                personFagsystemId = "tilfeldig",
                arbeidsgiverFagsystemId = "77ATRH3QENHB5K4XUY4LQ7HRTY",
                organisasjonsnavn = "PENGELØS SPAREBANK",
                navn = "Molefonken Ert"
            )

            assertEquals(expectedPdfPayload, pdfPayload)
        }
    }

    @Test
    fun `behandler annullering av brukerutbetaling`() {
        runBlocking {
            testRapid.sendTestMessage(brukerannullering())

            val pdfRequest = capturedPdfRequests.single()
            val pdfPayload =
                requireNotNull(objectMapper.readValue(pdfRequest.body.toByteArray(), AnnulleringPdfPayload::class.java))

            val expectedPdfPayload = AnnulleringPdfPayload(
                fødselsnummer = "fnr",
                fom = LocalDate.of(2020, 1, 1),
                tom = LocalDate.of(2020, 1, 10),
                organisasjonsnummer = "123456789",
                dato = LocalDateTime.of(2020, 5, 4, 8, 8, 0),
                epost = "sara.saksbehandler@nav.no",
                ident = "A123456",
                personFagsystemId = "77ATRH3QENHB5K4XUY4LQ7HRTY",
                arbeidsgiverFagsystemId = "tilfeldig",
                organisasjonsnavn = "PENGELØS SPAREBANK",
                navn = "Molefonken Ert"
            )

            assertEquals(expectedPdfPayload, pdfPayload)
        }
    }

    @Test
    fun `oppretter ikke ny journalpost ved behandling av duplikat melding`() {
        val annullering = annullering()
        testRapid.sendTestMessage(annullering)
        testRapid.sendTestMessage(annullering)

        assertEquals(1, capturedJoarkRequests.size)
    }

    @Language("JSON")
    private fun annullering(id: UUID = UUID.randomUUID(), utbetalingId: UUID = UUID.randomUUID()) = """
        {
            "@event_name": "utbetaling_annullert",
            "@opprettet": "2020-05-04T11:26:47.088455",
            "@id": "$id",
            "utbetalingId": "$utbetalingId",
            "fødselsnummer": "fnr",
            "aktørId": "aktørid",
            "organisasjonsnummer": "123456789",
            "tidspunkt": "2020-05-04T08:08:00.00000",
            "fom": "2020-01-01",
            "tom": "2020-01-10",
            "epost": "sara.saksbehandler@nav.no",
            "ident": "A123456",
            "arbeidsgiverFagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
            "personFagsystemId": "tilfeldig" 
        }
    """

    @Language("JSON")
    private fun brukerannullering(id: UUID = UUID.randomUUID()) = """
        {
            "@event_name": "utbetaling_annullert",
            "@opprettet": "2020-05-04T11:26:47.088455",
            "@id": "$id",
            "utbetalingId": "${UUID.randomUUID()}",
            "fødselsnummer": "fnr",
            "aktørId": "aktørid",
            "organisasjonsnummer": "123456789",
            "tidspunkt": "2020-05-04T08:08:00.00000",
            "fom": "2020-01-01",
            "tom": "2020-01-10",
            "epost": "sara.saksbehandler@nav.no",
            "ident": "A123456",
            "arbeidsgiverFagsystemId": "tilfeldig",
            "personFagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY" 
        }
    """

    private fun expectedJournalpost(eksternReferanseId: UUID = UUID.randomUUID()): JournalpostPayload {
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
            ),
            eksternReferanseId = eksternReferanseId.toString(),
        )
    }

    private val testRapid = TestRapid()
    private lateinit var dataSource: TestDataSource
    private var capturedJoarkRequests = mutableListOf<HttpRequestData>()
    private var capturedPdfRequests = mutableListOf<HttpRequestData>()

    private val mockClient = httpclient()
    private val pdfClient = PdfClient(mockClient, "http://url.no")
    private val azureMock: AzureTokenProvider = mockk {
        every { bearerToken("scope") }.returns(AzureToken("token", LocalDateTime.MAX).ok())
        every { bearerToken("JOARK_SCOPE") }.returns(
            AzureToken(
                "6B70C162-8AAB-4B56-944D-7F092423FE4B",
                LocalDateTime.MAX
            ).ok()
        )
    }
    private val joarkClient = JoarkClient("https://url.no", azureMock, "JOARK_SCOPE", mockClient)
    private val eregClient = EregClient("https://url.no", mockClient)
    private val speedClient = mockk<SpeedClient> {
        every { hentPersoninfo(any(), any()) } returns PersonResponse(
            fødselsdato = LocalDate.now(),
            dødsdato = null,
            fornavn = "MOLEFONKEN",
            mellomnavn = null,
            etternavn = "ERT",
            adressebeskyttelse = PersonResponse.Adressebeskyttelse.UGRADERT,
            kjønn = PersonResponse.Kjønn.UKJENT
        ).ok()
    }

    private lateinit var duplikatsjekkDao: DuplikatsjekkDao
    private lateinit var vedtakFattetDao: VedtakFattetDao
    private lateinit var utbetalingDao: UtbetalingDao
    private lateinit var annulleringDao: AnnulleringDao
    private lateinit var planlagtAnnulleringDao: PlanlagtAnnulleringDao
    private val feriepengerMediator = FeriepengerMediator(pdfClient, joarkClient)

    @BeforeEach
    internal fun setup() {
        dataSource = databaseContainer.nyTilkobling()

        duplikatsjekkDao = DuplikatsjekkDao(dataSource.ds)
        vedtakFattetDao = VedtakFattetDao(dataSource.ds)
        utbetalingDao = UtbetalingDao(dataSource.ds)
        annulleringDao = AnnulleringDao(dataSource.ds)
        planlagtAnnulleringDao = PlanlagtAnnulleringDao(dataSource.ds)

        testRapid.settOppRivers(
            duplikatsjekkDao,
            feriepengerMediator,
            vedtakFattetDao,
            utbetalingDao,
            annulleringDao,
            planlagtAnnulleringDao,
            pdfClient,
            joarkClient,
            eregClient,
            speedClient,
            nyAnnullering = false
        )
        capturedJoarkRequests.clear()
        capturedPdfRequests.clear()

        testRapid.reset()
    }

    @AfterEach
    fun after() {
        databaseContainer.droppTilkobling(dataSource)
        testRapid.reset()
    }

    private fun httpclient(): HttpClient {
        return HttpClient(MockEngine) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(objectMapper))
            }
            engine {
                addHandler { request ->
                    when (request.url.fullPath) {

                        "/rest/journalpostapi/v1/journalpost?forsoekFerdigstill=true" -> handlerForJoark(request)

                        "/api/v1/genpdf/spre-gosys/vedtak",
                        "/api/v1/genpdf/spre-gosys/ferdig-annullering",
                        "/api/v1/genpdf/spre-gosys/annullering" -> handlerForPdfKall(request)

                        "/v1/organisasjon/123456789" -> handlerForEregKall(request)

                        else -> error("Unhandled ${request.url.fullPath}")
                    }
                }
            }
        }
    }

    private fun MockRequestHandleScope.handlerForJoark(request: HttpRequestData): HttpResponseData {
        capturedJoarkRequests.add(request)
        return respond("Hello, world")
    }

    private fun MockRequestHandleScope.handlerForPdfKall(request: HttpRequestData): HttpResponseData {
        capturedPdfRequests.add(request)
        return respond("Test".toByteArray())
    }

    private fun MockRequestHandleScope.handlerForEregKall(request: HttpRequestData): HttpResponseData {
        return respond(eregResponse().toByteArray())
    }
}
