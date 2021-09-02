package no.nav.helse.spre.gosys.e2e

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtbetaltRiver
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetRiver
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertFalse

internal class VedtakOgUtbetalingJoarkFeilhåndteringTest : AbstractE2ETest() {

    private val vedtakFattetDao = VedtakFattetDao(dataSource)
    private val utbetalingDao = UtbetalingDao(dataSource)

    init {
        VedtakFattetRiver(testRapid, vedtakFattetDao, utbetalingDao, duplikatsjekkDao, vedtakMediator)
        UtbetalingUtbetaltRiver(testRapid, utbetalingDao, vedtakFattetDao, duplikatsjekkDao, vedtakMediator)
    }

    @Test
    fun `gir opp og lar appen restarte om Joark-kallet feiler`() {
        val vedtakFattetHendelseId = UUID.randomUUID()
        val (vedtakFattetEvent, utbetalingUtbetaltEvent) = testdata(vedtakFattetHendelseId)
        assertThrows<IllegalStateException> {
            testRapid.sendTestMessage(utbetalingUtbetaltEvent)
            testRapid.sendTestMessage(vedtakFattetEvent)
        }
        assertFalse(harLagretTilDuplikattabellen(vedtakFattetHendelseId))
    }

    override fun MockRequestHandleScope.handlerForJoark(request: HttpRequestData): HttpResponseData {
        capturedJoarkRequests.add(request)
        error("connection reset")
    }

    private fun harLagretTilDuplikattabellen(hendelseId: UUID): Boolean = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT COUNT(1) FROM duplikatsjekk WHERE id=?"
        session.run(queryOf(query, hendelseId).map { it.int(1) }.asSingle)
    } == 1

    private fun testdata(vedtakFattetHendelseId: UUID): Pair<String, String> {
        val utbetalingId = UUID.randomUUID()
        return basicVedtak(
            id = vedtakFattetHendelseId,
            utbetalingId = utbetalingId
        ) to utbetalingUtbetalt(utbetalingId = utbetalingId)
    }

    @Language("JSON")
    private fun basicVedtak(
        id: UUID = UUID.randomUUID(),
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        utbetalingId: UUID
    ) = """{
    "@id": "$id",
    "vedtaksperiodeId": "$vedtaksperiodeId",
    "fødselsnummer": "12345678910",
    "@event_name": "vedtak_fattet",
    "@opprettet": "2021-05-25T13:12:24.922420993",
    "utbetalingId": "$utbetalingId",
    "fom": "2021-05-06",
    "tom": "2021-05-16",
    "@forårsaket_av": {
        "event_name": "behov"
    },
    "hendelser": [
        "65ca68fa-0f12-40f3-ac34-141fa77c4270",
        "6977170d-5a99-4e7f-8d5f-93bda94a9ba3",
        "15aa9c84-a9cc-4787-b82a-d5447aa3fab1"
    ],
    "skjæringstidspunkt": "2021-01-07",
    "sykepengegrunnlag": 565260.0,
    "inntekt": 47105.0,
    "aktørId": "123",
    "organisasjonsnummer": "123456789",
    "system_read_count": 0
}
"""

    @Language("json")
    fun utbetalingUtbetalt(
        id: UUID = UUID.randomUUID(),
        utbetalingId: UUID = UUID.randomUUID()
    ) = """{
    "@id": "$id",
    "fødselsnummer": "12345678910",
    "utbetalingId": "$utbetalingId",
    "@event_name": "utbetaling_utbetalt",
    "fom": "2021-05-06",
    "tom": "2021-05-16",
    "maksdato": "2021-07-15",
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
                "fom": "2021-05-06",
                "tom": "2021-05-16",
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
        "fom": "2021-05-06",
        "tom": "2021-05-16"
    },
    "utbetalingsdager": [
        {
            "dato": "2021-05-06",
            "type": "NavDag"
        },
        {
            "dato": "2021-05-07",
            "type": "NavDag"
        },
        {
            "dato": "2021-05-08",
            "type": "NavHelgeDag"
        },
        {
            "dato": "2021-05-09",
            "type": "NavHelgeDag"
        },
        {
            "dato": "2021-05-10",
            "type": "NavDag"
        },
        {
            "dato": "2021-05-11",
            "type": "NavDag"
        },
        {
            "dato": "2021-05-12",
            "type": "NavDag"
        },
        {
            "dato": "2021-05-13",
            "type": "NavDag"
        },
        {
            "dato": "2021-05-14",
            "type": "NavDag"
        },
        {
            "dato": "2021-05-15",
            "type": "NavHelgeDag"
        },
        {
            "dato": "2021-05-16",
            "type": "NavHelgeDag"
        }
    ],
    "@opprettet": "${LocalDateTime.now()}",
    "aktørId": "123",
    "organisasjonsnummer": "123456789"
}
    """

}
