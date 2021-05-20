package no.nav.helse.spre.gosys.utbetaling

import no.nav.helse.spre.gosys.e2e.AbstractE2ETest
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertNotNull

internal class UtbetalingUtbetaltRiverTest: AbstractE2ETest() {

    val utbetalingDao = UtbetalingDao(dataSource)
    val vedtakFattetDao = VedtakFattetDao(dataSource)

    init {
        UtbetalingUtbetaltRiver(testRapid, utbetalingDao, vedtakFattetDao, vedtakMediator)
    }

    @Test
    fun `Lagrer utbetaling utbetalt`() {
        val utbetalingId = UUID.randomUUID()
        testRapid.sendTestMessage(utbetalingUtbetalt(utbetalingId = utbetalingId))
        assertNotNull(utbetalingDao.finnUtbetalingData(utbetalingId))
    }

    @Language("json")
    private fun utbetalingUtbetalt(
        id: UUID = UUID.randomUUID(),
        utbetalingId: UUID = UUID.randomUUID()
    ) = """{
  "@id": "$id",
  "fødselsnummer": "12345678910",
  "utbetalingId": "$utbetalingId",
  "@event_name": "utbetaling_utbetalt",
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
    "linjer": [
      {
        "fom": "2021-01-01",
        "tom": "2021-01-07",
        "dagsats": 1431,
        "lønn": 2193,
        "grad": 100.0,
        "stønadsdager": 35,
        "totalbeløp": 38360,
        "endringskode": "UEND",
        "delytelseId": 1,
        "klassekode": "SPREFAG-IOP"
      }
    ],
    "fagsystemId": "123",
    "endringskode": "ENDR",
    "tidsstempel": "${LocalDateTime.now()}",
    "nettoBeløp": "38360",
    "stønadsdager": 35,
    "fom": "2021-01-01",
    "tom": "2021-01-07"
  },
  "utbetalingsdager": [
        {
          "dato": "2021-01-01",
          "type": "NavDag",
          "begrunnelser": null

        },
        {
          "dato": "2021-01-02",
          "type": "NavHelgDag",
          "begrunnelser": null

        },
        {
          "dato": "2021-01-03",
          "type": "NavHelgDag",
          "begrunnelser": null

        },
        {
          "dato": "2021-01-04",
          "type": "NavDag",
          "begrunnelser": null

        },
        {
          "dato": "2021-01-05",
          "type": "NavDag",
          "begrunnelser": null

        },
        {
          "dato": "2021-01-06",
          "type": "NavDag",
          "begrunnelser": null

        },
        {
          "dato": "2021-01-07",
          "type": "NavDag",
          "begrunnelser": null
        }
  ],
  "@opprettet": "${LocalDateTime.now()}",
  "aktørId": "123",
  "organisasjonsnummer": "123456789"
}
    """




}
