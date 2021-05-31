package no.nav.helse.spre.gosys.utbetaling

import no.nav.helse.spre.gosys.e2e.AbstractE2ETest
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertNotNull

internal class UtbetalingUtenUtbetalingRiverTest: AbstractE2ETest() {

    val utbetalingDao = UtbetalingDao(dataSource)
    val vedtakFattetDao = VedtakFattetDao(dataSource)

    init {
        UtbetalingUtenUtbetalingRiver(testRapid, utbetalingDao, vedtakFattetDao, vedtakMediator)
    }

    @Test
    fun `Lagrer utbetaling utbetalt`() {
        val utbetalingId = UUID.randomUUID()
        testRapid.sendTestMessage(utbetalingUtenUtbetaling(utbetalingId = utbetalingId))
        assertNotNull(utbetalingDao.finnUtbetalingData(utbetalingId))
    }

    @Language("json")
    private fun utbetalingUtenUtbetaling(
        id: UUID = UUID.randomUUID(),
        utbetalingId: UUID = UUID.randomUUID()
    ) = """{
  "@id": "$id",
  "fødselsnummer": "12345678910",
  "utbetalingId": "$utbetalingId",
  "@event_name": "utbetaling_uten_utbetaling",
  "fom": "2021-01-01",
  "tom": "2021-01-07",
  "maksdato": "2021-05-07",
  "forbrukteSykedager": "217",
  "gjenståendeSykedager": "31",
  "ident": "Automatisk behandlet",
  "epost": "tbd@nav.no",
  "type": "UTBETALING",
  "tidspunkt": "${LocalDateTime.now()}",
  "automatiskBehandling": "true",
  "arbeidsgiverOppdrag": {
    "mottaker": "123456789",
    "fagområde": "SPREF",
    "linjer": [],
    "fagsystemId": "123",
    "endringskode": "ENDR",
    "tidsstempel": "${LocalDateTime.now()}",
    "nettoBeløp": "0",
    "stønadsdager": 0,
    "fom": "2021-01-01",
    "tom": "2021-01-07"
  },
  "utbetalingsdager": [
        {
          "dato": "2021-01-01",
          "type": "Fridag",
          "begrunnelser": null
        },
        {
          "dato": "2021-01-02",
          "type": "Fridag",
          "begrunnelser": null

        },
        {
          "dato": "2021-01-03",
          "type": "Fridag",
          "begrunnelser": null

        },
        {
          "dato": "2021-01-04",
          "type": "Fridag",
          "begrunnelser": null

        },
        {
          "dato": "2021-01-05",
          "type": "Fridag",
          "begrunnelser": null

        },
        {
          "dato": "2021-01-06",
          "type": "Fridag",
          "begrunnelser": null

        },
        {
          "dato": "2021-01-07",
          "type": "Fridag",
          "begrunnelser": null
        }
  ],
  "@opprettet": "${LocalDateTime.now()}",
  "aktørId": "123",
  "organisasjonsnummer": "123456789"
}
    """




}
