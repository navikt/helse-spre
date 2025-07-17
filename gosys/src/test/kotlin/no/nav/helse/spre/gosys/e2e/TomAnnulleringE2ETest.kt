package no.nav.helse.spre.gosys.e2e

import io.ktor.client.engine.mock.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.JournalpostPayload
import no.nav.helse.spre.gosys.annullering.TomAnnulleringMessage.TomAnnulleringPdfPayload
import no.nav.helse.spre.gosys.objectMapper
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TomAnnulleringE2ETest  : AbstractE2ETest() {

    @BeforeEach
    fun setup() {
        testRapid.reset()
        capturedJoarkRequests.clear()
        capturedPdfRequests.clear()
    }

    @Test
    fun `journalfører en tom annullering`() {
        runBlocking {
            val hendelseId = UUID.randomUUID()
            testRapid.sendTestMessage(vedtaksperiodeAnnullertMars(id = hendelseId))
            val joarkRequest = capturedJoarkRequests.single()
            val joarkPayload =
                requireNotNull(objectMapper.readValue(joarkRequest.body.toByteArray(), JournalpostPayload::class.java))

            Assertions.assertEquals("Bearer 6B70C162-8AAB-4B56-944D-7F092423FE4B", joarkRequest.headers["Authorization"])
            Assertions.assertEquals(hendelseId.toString(), joarkRequest.headers["Nav-Consumer-Token"])
            Assertions.assertEquals("application/json", joarkRequest.body.contentType.toString())
            Assertions.assertEquals(expectedJournalpost(eksternReferanseId = hendelseId), joarkPayload)

            val pdfRequest = capturedPdfRequests.single()
            val pdfPayload =
                requireNotNull(objectMapper.readValue(pdfRequest.body.toByteArray(), TomAnnulleringPdfPayload::class.java))

            val expectedPdfPayload = TomAnnulleringPdfPayload(
                fødselsnummer = "fnr",
                fom = LocalDate.of(2018, 3, 1),
                tom = LocalDate.of(2018, 3, 31),
                organisasjonsnummer = "123456789",
                dato = LocalDateTime.of(2020, 5, 4, 8, 8, 0),
                epost = "tbd@nav.no",
                ident = "SPLEIS",
                personFagsystemId = null,
                arbeidsgiverFagsystemId = null,
                organisasjonsnavn = "PENGELØS SPAREBANK",
                navn = "Molefonken Ert"
            )

            Assertions.assertEquals(expectedPdfPayload, pdfPayload)
        }
    }

    @Test
    fun `Allerede laget annulleringsnotat for mars`() {
        testRapid.sendTestMessage(annulleringJanuarTilMars())

        val annulleringer = annulleringDao.finnAnnulleringHvisFinnes("fnr", "123456789")
        assertEquals(1, annulleringer.size)

        testRapid.sendTestMessage(vedtaksperiodeAnnullertMars())
        Assertions.assertEquals(1, capturedJoarkRequests.size)
    }

    @Test
    fun `Ikke laget annulleringsnotat for mars fra før`() {
        val annulleringer = annulleringDao.finnAnnulleringHvisFinnes("fnr", "123456789")
        assertEquals(0, annulleringer.size)

        testRapid.sendTestMessage(vedtaksperiodeAnnullertMars())
        Assertions.assertEquals(1, capturedJoarkRequests.size)
    }

    @Language("JSON")
    private fun annulleringJanuarTilMars(id: UUID = UUID.randomUUID(), utbetalingId: UUID = UUID.randomUUID()) = """
        {
            "@event_name": "utbetaling_annullert",
            "@opprettet": "2020-05-04T11:26:47.088455",
            "@id": "$id",
            "utbetalingId": "$utbetalingId",
            "fødselsnummer": "fnr",
            "aktørId": "aktørid",
            "organisasjonsnummer": "123456789",
            "tidspunkt": "2020-05-04T08:08:00.00000",
            "fom": "2018-01-01",
            "tom": "2018-03-31",
            "epost": "sara.saksbehandler@nav.no",
            "ident": "A123456",
            "arbeidsgiverFagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
            "personFagsystemId": "tilfeldig" 
        }
    """

    @Language("JSON")
    private fun vedtaksperiodeAnnullertMars(id: UUID = UUID.randomUUID(), vedtaksperiodeId: UUID = UUID.randomUUID()) = """
        {
          "@event_name": "vedtaksperiode_annullert",
          "organisasjonsnummer": "123456789",
          "yrkesaktivitetstype": "ARBEIDSTAKER",
          "vedtaksperiodeId": "$vedtaksperiodeId",
          "tilstand": "TIL_ANNULLERING",
          "hendelser": [],
          "fom": "2018-03-01",
          "tom": "2018-03-31",
          "trengerArbeidsgiveropplysninger": false,
          "speilrelatert": false,
          "sykmeldingsperioder": [],
          "@id": "$id",
          "@opprettet": "${LocalDateTime.of(2020, 5, 4, 8, 8, 0)}",
          "fødselsnummer": "fnr"
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
                    tittel = "Utbetaling annullert i ny løsning 01.03.2018 - 31.03.2018",
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
}
