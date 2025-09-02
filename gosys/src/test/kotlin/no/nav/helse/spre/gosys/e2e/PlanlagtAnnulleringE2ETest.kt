package no.nav.helse.spre.gosys.e2e

import io.ktor.client.engine.mock.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.JournalpostPayload
import no.nav.helse.spre.gosys.annullering.PlanlagtAnnullering.FerdigAnnulleringPdfPayload
import no.nav.helse.spre.gosys.objectMapper
import no.nav.helse.spre.testhelpers.januar
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled
internal class PlanlagtAnnulleringE2ETest : AbstractE2ETest() {

    @BeforeEach
    fun setup() {
        testRapid.reset()
        capturedJoarkRequests.clear()
        capturedPdfRequests.clear()
    }

    @Test
    fun `journalfører annullering - en vedtaksperiode annulleres`() {
        runBlocking {
            val hendelseId = UUID.randomUUID()
            val vedtaksperiodeId = UUID.randomUUID()

            testRapid.sendTestMessage(planlagtAnnullering(id = hendelseId, vedtaksperioder = listOf(vedtaksperiodeId), fom = 1.januar, tom = 10.januar))
            testRapid.sendTestMessage(vedtaksperiodeAnnullert(vedtaksperiodeId = vedtaksperiodeId, fom = 1.januar, tom = 10.januar))

            val joarkRequest = capturedJoarkRequests.single()
            val joarkPayload =
                requireNotNull(objectMapper.readValue(joarkRequest.body.toByteArray(), JournalpostPayload::class.java))

            Assertions.assertEquals("Bearer 6B70C162-8AAB-4B56-944D-7F092423FE4B", joarkRequest.headers["Authorization"])
            Assertions.assertEquals(hendelseId.toString(), joarkRequest.headers["Nav-Consumer-Token"])
            Assertions.assertEquals("application/json", joarkRequest.body.contentType.toString())
            Assertions.assertEquals(expectedJournalpost(eksternReferanseId = hendelseId, fom = 1.januar, tom = 10.januar), joarkPayload)

            val pdfRequest = capturedPdfRequests.single()
            val pdfPayload =
                requireNotNull(objectMapper.readValue(pdfRequest.body.toByteArray(), FerdigAnnulleringPdfPayload::class.java))

            val expectedPdfPayload = FerdigAnnulleringPdfPayload(
                fødselsnummer = "fnr",
                yrkesaktivitet = "123456789",
                fom = LocalDate.of(2018, 1, 1),
                tom = LocalDate.of(2018, 1, 10),
                saksbehandlerIdent = "A123456",
                årsaker = listOf("Annet", "Yrkesskade"),
                begrunnelse = "Todo",
                annullert = LocalDateTime.of(2020, 5, 4, 8, 8, 0),
                organisasjonsnavn = "PENGELØS SPAREBANK",
                navn = "Molefonken Ert"
            )

            Assertions.assertEquals(expectedPdfPayload, pdfPayload)
        }
    }

    @Test
    fun `journalfører annullering - to vedtaksperioder annulleres`() {
        runBlocking {
            val hendelseId = UUID.randomUUID()
            val vedtaksperiodeId1 = UUID.randomUUID()
            val vedtaksperiodeId2 = UUID.randomUUID()

            testRapid.sendTestMessage(planlagtAnnullering(id = hendelseId, vedtaksperioder = listOf(vedtaksperiodeId1, vedtaksperiodeId2), fom = 1.januar, tom = 20.januar))
            testRapid.sendTestMessage(vedtaksperiodeAnnullert(vedtaksperiodeId = vedtaksperiodeId1, fom = 1.januar, tom = 10.januar))

            assertEquals(0, capturedJoarkRequests.size)

            testRapid.sendTestMessage(vedtaksperiodeAnnullert(vedtaksperiodeId = vedtaksperiodeId2, fom = 11.januar, tom = 20.januar))

            val joarkRequest = capturedJoarkRequests.single()
            val joarkPayload =
                requireNotNull(objectMapper.readValue(joarkRequest.body.toByteArray(), JournalpostPayload::class.java))

            Assertions.assertEquals("Bearer 6B70C162-8AAB-4B56-944D-7F092423FE4B", joarkRequest.headers["Authorization"])
            Assertions.assertEquals(hendelseId.toString(), joarkRequest.headers["Nav-Consumer-Token"])
            Assertions.assertEquals("application/json", joarkRequest.body.contentType.toString())
            Assertions.assertEquals(expectedJournalpost(eksternReferanseId = hendelseId, fom = 1.januar, tom = 20.januar), joarkPayload)

            val pdfRequest = capturedPdfRequests.single()
            val pdfPayload =
                requireNotNull(objectMapper.readValue(pdfRequest.body.toByteArray(), FerdigAnnulleringPdfPayload::class.java))

            val expectedPdfPayload = FerdigAnnulleringPdfPayload(
                fødselsnummer = "fnr",
                yrkesaktivitet = "123456789",
                fom = LocalDate.of(2018, 1, 1),
                tom = LocalDate.of(2018, 1, 20),
                saksbehandlerIdent = "A123456",
                årsaker = listOf("Annet", "Yrkesskade"),
                begrunnelse = "Todo",
                annullert = LocalDateTime.of(2020, 5, 4, 8, 8, 0),
                organisasjonsnavn = "PENGELØS SPAREBANK",
                navn = "Molefonken Ert"
            )

            Assertions.assertEquals(expectedPdfPayload, pdfPayload)
        }
    }

    @Test
    fun `journalfører ikke notat når det allerede er opprettet`() {
        runBlocking {
            val hendelseId = UUID.randomUUID()
            val vedtaksperiodeId1 = UUID.randomUUID()

            testRapid.sendTestMessage(planlagtAnnullering(id = hendelseId, vedtaksperioder = listOf(vedtaksperiodeId1), fom = 1.januar, tom = 20.januar))
            testRapid.sendTestMessage(vedtaksperiodeAnnullert(vedtaksperiodeId = vedtaksperiodeId1, fom = 1.januar, tom = 20.januar))
            testRapid.sendTestMessage(vedtaksperiodeAnnullert(vedtaksperiodeId = vedtaksperiodeId1, fom = 1.januar, tom = 20.januar))

            assertEquals(1, capturedJoarkRequests.size)
        }
    }

    @Language("JSON")
    private fun planlagtAnnullering(id: UUID = UUID.randomUUID(), vedtaksperioder: List<UUID>, fom: LocalDate, tom: LocalDate) = """
        {
            "@event_name": "planlagt_annullering",
            "@opprettet": "${LocalDateTime.of(2020, 5, 4, 8, 8, 0)}",
            "@id": "$id",
            "fødselsnummer": "fnr",
            "yrkesaktivitet": "123456789",
            "tidspunkt": "2020-05-04T08:08:00.00000",
            "fom": "$fom",
            "tom": "$tom",
            "ident": "A123456",
            "årsaker": ["Annet", "Yrkesskade"],
            "begrunnelse": "Todo",
            "vedtaksperioder": ${vedtaksperioder.map { "\"$it\"" }}
        }
    """

    @Language("JSON")
    private fun vedtaksperiodeAnnullert(id: UUID = UUID.randomUUID(), vedtaksperiodeId: UUID = UUID.randomUUID(), fom: LocalDate, tom: LocalDate) = """
        {
          "@event_name": "vedtaksperiode_annullert",
          "organisasjonsnummer": "123456789",
          "yrkesaktivitetstype": "ARBEIDSTAKER",
          "vedtaksperiodeId": "$vedtaksperiodeId",
          "tilstand": "TIL_ANNULLERING",
          "hendelser": [],
          "fom": "$fom",
          "tom": "$tom",
          "trengerArbeidsgiveropplysninger": false,
          "speilrelatert": false,
          "sykmeldingsperioder": [],
          "@id": "$id",
          "@opprettet": "${LocalDateTime.of(2020, 5, 4, 8, 8, 0)}",
          "fødselsnummer": "fnr"
        }
    """

    private fun expectedJournalpost(eksternReferanseId: UUID = UUID.randomUUID(), fom: LocalDate, tom: LocalDate): JournalpostPayload {
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
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
                    tittel = "Utbetaling annullert i ny løsning ${fom.format(formatter)} - ${tom.format(formatter)}",
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
