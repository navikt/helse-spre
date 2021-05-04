package no.nav.helse.spre.gosys.feriepenger

import io.mockk.*
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.JoarkClient
import no.nav.helse.spre.gosys.PdfClient
import no.nav.helse.spre.gosys.setupDataSourceMedFlyway
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

internal class FeriepengerRiverTest {
    private val testRapid = TestRapid()
    private val joarkClient = mockk<JoarkClient>()
    private val pdfClient = mockk<PdfClient>(relaxed = true)
    val dataSource = setupDataSourceMedFlyway()
    val duplikatsjekkDao = DuplikatsjekkDao(dataSource)
    private val feriepengerMediator = FeriepengerMediator(pdfClient, joarkClient, duplikatsjekkDao)

    init {
        FeriepengerRiver(testRapid, feriepengerMediator)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
        coEvery { pdfClient.hentFeriepengerPdf(any()) } returns "PDF‽‽‽"
        coEvery { joarkClient.opprettJournalpost(any(), any()) } returns true
    }

    @Test
    fun `plukker opp utbetaling av feriepenger en gang`() {
        val feriepenger = feriepenger()
        testRapid.sendTestMessage(feriepenger)
        testRapid.sendTestMessage(feriepenger)

        coVerify(exactly = 1) { joarkClient.opprettJournalpost(any(), any()) }
    }

    @Test
    fun `oversetter behov med kun arbeidsgiverlinjer til riktig pdf-format`() {
        val utbetalt = LocalDate.of(2021, 5, 31).atTime(13, 37)
        val capturedPdfPayload = slot<FeriepengerPdfPayload>()
        coEvery { pdfClient.hentFeriepengerPdf(capture(capturedPdfPayload)) } returns "PDF‽‽‽"

        testRapid.sendTestMessage(feriepenger(utbetalt))
        assertEquals(
            FeriepengerPdfPayload(
                tittel = "Feriepenger utbetalt for sykepenger",
                oppdrag = listOf(
                    Oppdrag(
                        type = OppdragType.ARBEIDSGIVER,
                        fom = LocalDate.of(2021, 5, 1),
                        tom = LocalDate.of(2021, 5, 31),
                        totalbeløp = 1000,
                        mottaker = "123456789"
                    )
                ),
                utbetalt = utbetalt
            ), capturedPdfPayload.captured
        )
    }

    @Test
    fun `oversetter behov med både arbeidsgiver- og personlinjer til riktig pdf-format`() {
        val utbetalt = LocalDate.of(2021, 5, 31).atTime(13, 37)
        val capturedPdfPayload = slot<FeriepengerPdfPayload>()
        coEvery { pdfClient.hentFeriepengerPdf(capture(capturedPdfPayload)) } returns "PDF‽‽‽"

        testRapid.sendTestMessage(feriepengerArbeidsgiverOgPerson(utbetalt))
        assertEquals(
            FeriepengerPdfPayload(
                tittel = "Feriepenger utbetalt for sykepenger",
                oppdrag = listOf(
                    Oppdrag(
                        type = OppdragType.ARBEIDSGIVER,
                        fom = LocalDate.of(2021, 5, 1),
                        tom = LocalDate.of(2021, 5, 31),
                        totalbeløp = 1000,
                        mottaker = "123456789"
                    ),
                    Oppdrag(
                        type = OppdragType.PERSON,
                        fom = LocalDate.of(2021, 5, 1),
                        tom = LocalDate.of(2021, 5, 31),
                        totalbeløp = 420,
                        mottaker = "20046912345"
                    )
                ),
                utbetalt = utbetalt
            ), capturedPdfPayload.captured
        )
    }

    @Language("JSON")
    private fun feriepenger(
        utbetalt: LocalDateTime = LocalDate.of(2021, 5, 31).atTime(13, 37)
    ) = """
    {
      "type": "FERIEPENGER",
      "forrigeStatus": "OVERFØRT",
      "gjeldendeStatus": "UTBETALT",
      "arbeidsgiverOppdrag": {
        "mottaker": "123456789",
        "linjer": [
          {
            "fom": "2021-05-01",
            "tom": "2021-05-31",
            "totalbeløp": 1000
          }
        ],
        "fagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
        "tidsstempel": "$utbetalt",
        "fom": "2021-05-01",
        "tom": "2021-05-31"
      },
      "personOppdrag": {
        "mottaker": "20046912345",
        "linjer": [],
        "fagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
        "tidsstempel": "$utbetalt",
        "fom": "-999999999-01-01",
        "tom": "-999999999-01-01"
      },
      "@event_name": "utbetaling_endret",
      "@id": "${UUID.randomUUID()}",
      "@opprettet": "2021-05-31T13:37:00.000000000",
      "aktørId": "123456",
      "fødselsnummer": "20046912345",
      "organisasjonsnummer": "123456789"
    }
    """

    @Language("JSON")
    private fun feriepengerArbeidsgiverOgPerson(
        utbetalt: LocalDateTime = LocalDate.of(2021, 5, 31).atTime(13, 37)
    ) = """
    {
      "type": "FERIEPENGER",
      "forrigeStatus": "OVERFØRT",
      "gjeldendeStatus": "UTBETALT",
      "arbeidsgiverOppdrag": {
        "mottaker": "123456789",
        "linjer": [
          {
            "fom": "2021-05-01",
            "tom": "2021-05-31",
            "totalbeløp": 1000
          }
        ],
        "fagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
        "tidsstempel": "$utbetalt",
        "fom": "2021-05-01",
        "tom": "2021-05-31"
      },
      "personOppdrag": {
        "mottaker": "20046912345",
        "linjer": [
          {
            "fom": "2021-05-01",
            "tom": "2021-05-31",
            "totalbeløp": 420
          }
        ],
        "fagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
        "tidsstempel": "$utbetalt",
        "fom": "2021-05-01",
        "tom": "2021-05-31"
      },
      "@event_name": "utbetaling_endret",
      "@id": "${UUID.randomUUID()}",
      "@opprettet": "2021-05-31T13:37:00.000000000",
      "aktørId": "123456",
      "fødselsnummer": "20046912345",
      "organisasjonsnummer": "123456789"
    }
    """

}
