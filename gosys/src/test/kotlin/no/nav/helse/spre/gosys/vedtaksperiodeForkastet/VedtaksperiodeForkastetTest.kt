package no.nav.helse.spre.gosys.vedtaksperiodeForkastet

import java.util.UUID
import kotlin.test.assertEquals
import no.nav.helse.spre.gosys.e2e.AbstractE2ETest
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

internal class VedtaksperiodeForkastetTest  : AbstractE2ETest() {

    @Test
    fun `superenkelt testcase navn tbd`() {
        val utbetalingIdUtbetaling = UUID.randomUUID().toString()
        val utbetalingIdRevurdering = UUID.randomUUID().toString()
        val korrelasjonsId = UUID.randomUUID().toString()

        testRapid.sendTestMessage(utbetalingJanuarTilMars(utbetalingIdUtbetaling, korrelasjonsId))
        testRapid.sendTestMessage(utbetalingRevurdering(utbetalingIdRevurdering, korrelasjonsId))

        val utbetalinger = utbetalingDao.finnUtbetalingData("12345678901", "a1")

        assertEquals(2, utbetalinger.size)

        testRapid.sendTestMessage(vedtaksperiodeForkastetMars())
    }


    @Language("JSON")
    fun utbetalingJanuarTilMars(utbetalingId: String, korrelasjonsId: String) = """
      {
        "@event_name": "utbetaling_utbetalt",
        "organisasjonsnummer": "a1",
        "yrkesaktivitetstype": "ARBEIDSTAKER",
        "utbetalingId": "$utbetalingId",
        "korrelasjonsId": "$korrelasjonsId",
        "type": "UTBETALING",
        "fom": "2018-01-01",
        "tom": "2018-03-31",
        "maksdato": "2019-01-01",
        "forbrukteSykedager": 90,
        "gjenståendeSykedager": 170,
        "stønadsdager": 90,
        "ident": "Automatisk behandlet",
        "epost": "tbd@nav.no",
        "tidspunkt": "2018-07-17T09:49:12.489694803",
        "automatiskBehandling": true,
        "arbeidsgiverOppdrag": {
          "fagsystemId": "ARBEIDSGIVEROPPDRAG",
          "fagområde": "SPREF",
          "mottaker": "a1",
          "nettoBeløp": 21000,
          "stønadsdager": 90,
          "fom": "2018-01-01",
          "tom": "2018-03-31",
          "linjer": [
            {
              "fom": "2018-01-16",
              "tom": "2018-03-31",
              "sats": 1431,
              "grad": 100.0,
              "stønadsdager": 90,
              "totalbeløp": 217980,
              "statuskode": null
            }
          ]
        },
        "personOppdrag": {
          "fagsystemId": "BRUKERUTBETALING",
          "fagområde": "SP",
          "mottaker": "12345678901",
          "nettoBeløp": 0,
          "stønadsdager": 0,
          "fom": "-999999999-01-01",
          "tom": "-999999999-01-01",
          "linjer": []
        },
        "utbetalingsdager": [],
        "@id": "${UUID.randomUUID()}",
        "@opprettet": "2018-07-17T09:49:12.782439957",
        "fødselsnummer": "12345678901"
      }
    """

    @Language("JSON")
    fun utbetalingRevurdering(utbetalingId: String, korrelasjonsId: String) = """
      {
        "@event_name": "utbetaling_utbetalt",
        "organisasjonsnummer": "a1",
        "yrkesaktivitetstype": "ARBEIDSTAKER",
        "utbetalingId": "$utbetalingId",
        "korrelasjonsId": "$korrelasjonsId",
        "type": "REVURDERING",
        "fom": "2018-01-01",
        "tom": "2018-02-28",
        "maksdato": "2019-01-01",
        "forbrukteSykedager": 60,
        "gjenståendeSykedager": 200,
        "stønadsdager": 60,
        "ident": "Automatisk behandlet",
        "epost": "tbd@nav.no",
        "tidspunkt": "2018-07-18T09:49:12.489694803",
        "automatiskBehandling": true,
        "arbeidsgiverOppdrag": {
          "fagsystemId": "ARBEIDSGIVEROPPDRAG",
          "fagområde": "SPREF",
          "mottaker": "a1",
          "nettoBeløp": -10000,
          "stønadsdager": 60,
          "fom": "2018-01-01",
          "tom": "2018-02-28",
          "linjer": [
            {
              "fom": "2018-01-16",
              "tom": "2018-02-28",
              "sats": 1431,
              "grad": 100.0,
              "stønadsdager": 60,
              "totalbeløp": 207980,
              "statuskode": null
            }
          ]
        },
        "personOppdrag": {
          "fagsystemId": "BRUKERUTBETALING",
          "fagområde": "SP",
          "mottaker": "12345678901",
          "nettoBeløp": 0,
          "stønadsdager": 0,
          "fom": "-999999999-01-01",
          "tom": "-999999999-01-01",
          "linjer": []
        },
        "utbetalingsdager": [],
        "@id": "${UUID.randomUUID()}",
        "@opprettet": "2018-07-18T09:49:12.782439957",
        "fødselsnummer": "12345678901"
      }
    """

    @Language("JSON")
    fun vedtaksperiodeForkastetMars() = """
        {
          "@event_name": "vedtaksperiode_forkastet",
          "organisasjonsnummer": "a1",
          "yrkesaktivitetstype": "ARBEIDSTAKER",
          "vedtaksperiodeId": "${UUID.randomUUID()}",
          "tilstand": "TIL_ANNULLERING",
          "hendelser": [],
          "fom": "2018-03-01",
          "tom": "2018-03-31",
          "trengerArbeidsgiveropplysninger": false,
          "speilrelatert": false,
          "sykmeldingsperioder": [],
          "@id": "${UUID.randomUUID()}",
          "@opprettet": "2018-07-18T10:21:26.167840276",
          "fødselsnummer": "12345678901"
        }
    """
}
