package no.nav.helse.spre.gosys.feriepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.test_support.TestDataSource
import io.mockk.*
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.JoarkClient
import no.nav.helse.spre.gosys.PdfClient
import no.nav.helse.spre.gosys.databaseContainer
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class FeriepengerRiverTest {
    private val testRapid = TestRapid()
    private val joarkClient = mockk<JoarkClient>()
    private val pdfClient = mockk<PdfClient>(relaxed = true)
    protected lateinit var dataSource: TestDataSource

    @BeforeEach
    fun setup() {
        dataSource = databaseContainer.nyTilkobling()
        clearAllMocks()
        coEvery { pdfClient.hentFeriepengerPdf(any()) } returns "PDF‽‽‽"
        coEvery { joarkClient.opprettJournalpost(any(), any()) } returns true

        val feriepengerMediator = FeriepengerMediator(pdfClient, joarkClient)
        val duplikatsjekkDao = DuplikatsjekkDao(dataSource.ds)
        FeriepengerRiver(testRapid, duplikatsjekkDao, feriepengerMediator)
    }


    @AfterEach
    fun after() {
        databaseContainer.droppTilkobling(dataSource)
        testRapid.reset()
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
                    OppdragPdfPayload(
                        type = OppdragType.ARBEIDSGIVER,
                        fom = LocalDate.of(2021, 5, 1),
                        tom = LocalDate.of(2021, 5, 31),
                        totalbeløp = 1000,
                        mottaker = "123456789",
                        fagsystemId ="88ABRH3QENHB5K4XUY4LQ7HRTY"
                    )
                ),
                utbetalt = utbetalt,
                orgnummer = "123456789",
                fødselsnummer = "20046912345",

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
                    OppdragPdfPayload(
                        type = OppdragType.ARBEIDSGIVER,
                        fom = LocalDate.of(2021, 5, 1),
                        tom = LocalDate.of(2021, 5, 31),
                        totalbeløp = 1000,
                        mottaker = "123456789",
                        fagsystemId ="88ABRH3QENHB5K4XUY4LQ7HRTY"
                    ),
                    OppdragPdfPayload(
                        type = OppdragType.PERSON,
                        fom = LocalDate.of(2021, 5, 1),
                        tom = LocalDate.of(2021, 5, 31),
                        totalbeløp = 420,
                        mottaker = "20046912345",
                        fagsystemId ="77ATRH3QENHB5K4XUY4LQ7HRTY"

                    )
                ),
                utbetalt = utbetalt,
                orgnummer = "123456789",
                fødselsnummer = "20046912345"
            ), capturedPdfPayload.captured
        )
    }

    @Language("JSON")
    private fun feriepenger(
        utbetalt: LocalDateTime = LocalDate.of(2021, 5, 31).atTime(13, 37)
    ) = """
    {
      "arbeidsgiverOppdrag": {
        "mottaker": "123456789",
        "fagsystemId": "88ABRH3QENHB5K4XUY4LQ7HRTY",
        "totalbeløp": 1000
      },
      "personOppdrag": {
        "mottaker": "20046912345",
        "fagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
        "totalbeløp": 0
      },
      "@event_name": "feriepenger_utbetalt",
      "@id": "${UUID.randomUUID()}",
      "@opprettet": "$utbetalt",
      "aktørId": "123456",
      "fødselsnummer": "20046912345",
      "organisasjonsnummer": "123456789",
      "fom": "2021-05-01",
      "tom": "2021-05-31"
    }
    """

    @Language("JSON")
    private fun feriepengerArbeidsgiverOgPerson(
        utbetalt: LocalDateTime = LocalDate.of(2021, 5, 31).atTime(13, 37)
    ) = """
    {
      "arbeidsgiverOppdrag": {
        "mottaker": "123456789",
        "fagsystemId": "88ABRH3QENHB5K4XUY4LQ7HRTY",
        "totalbeløp": 1000
      },
      "personOppdrag": {
        "mottaker": "20046912345",
        "fagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
        "totalbeløp": 420
      },
      "@event_name": "feriepenger_utbetalt",
      "@id": "${UUID.randomUUID()}",
      "@opprettet": "$utbetalt",
      "aktørId": "123456",
      "fødselsnummer": "20046912345",
      "organisasjonsnummer": "123456789",
      "fom": "2021-05-01",
      "tom": "2021-05-31"
    }
    """

}
