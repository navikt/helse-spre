package feriepenger

import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.gosys.*
import no.nav.helse.spre.gosys.feriepenger.FeriepengerMediator
import no.nav.helse.spre.gosys.feriepenger.FeriepengerRiver
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.*

internal class FeriepengerRiverTest {

    private val testRapid = TestRapid()
    private val stsMock: StsRestClient = mockk {
        coEvery { token() }.returns("6B70C162-8AAB-4B56-944D-7F092423FE4B")
    }
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
    }

    @Disabled
    @Test
    fun `plukker opp utbetaling av feriepenger en gang`() {
        val capturedJoarkRequests = mutableListOf<UUID>()
        coEvery { joarkClient.opprettJournalpost(capture(capturedJoarkRequests), any()) }

        val feriepenger = feriepenger()
        testRapid.sendTestMessage(feriepenger)
        testRapid.sendTestMessage(feriepenger)

        Assertions.assertEquals(1, capturedJoarkRequests.size)
    }

    @Language("JSON")
    private fun feriepenger() = """
    {
      "type": "UTBETALING",
      "forrigeStatus": "OVERFØRT",
      "gjeldendeStatus": "UTBETALT",
      "arbeidsgiverOppdrag": {
        "mottaker": "123456789",
        "linjer": [
          {
            "fom": "2021-05-00",
            "tom": "2021-05-31",
            "totalbeløp": 1000
          }
        ],
        "fagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
        "tidsstempel": "2021-05-31T13:37:00.000000000",
        "fom": "2021-05-00",
        "tom": "2021-05-31"
      },
      "personOppdrag": {
        "mottaker": "20046912345",
        "linjer": [],
        "fagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
        "tidsstempel": "2021-05-31T13:37:00.000000000",
        "fom": "-999999999-01-01",
        "tom": "-999999999-01-01"
      },
      "@event_name": "utbetaling_endret",
      "@id": "6fdc54cc-6b44-479c-944d-60ea7858f163",
      "@opprettet": "2021-05-31T13:37:00.000000000",
      "aktørId": "123456",
      "fødselsnummer": "20046912345",
      "organisasjonsnummer": "123456789"
    }
    """

}