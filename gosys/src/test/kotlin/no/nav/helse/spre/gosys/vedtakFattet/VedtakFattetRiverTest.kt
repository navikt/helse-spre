package no.nav.helse.spre.gosys.vedtakFattet

import no.nav.helse.spre.gosys.e2e.AbstractE2ETest
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

internal class VedtakFattetRiverTest : AbstractE2ETest() {

    @Test
    fun `Lagrer vedtak fattet`() {
        val utbetalingId = UUID.randomUUID()
        sendUtbetaling(utbetalingId = utbetalingId)
        sendVedtakFattet(utbetalingId = utbetalingId)
        val vedtakFattetRad = vedtakFattetDao.finn(utbetalingId)
        assertNotNull(vedtakFattetRad)
        assertTrue(vedtakFattetRad!!.erJournalført())
    }

    @Test
    fun `kan ikke lagre flere vedtak knyttet til samme utbetaling`() {
        val utbetalingId = UUID.randomUUID()
        sendUtbetaling(utbetalingId = utbetalingId)
        sendVedtakFattet(utbetalingId = utbetalingId)
        assertThrows<IllegalStateException> { sendVedtakFattet(utbetalingId = utbetalingId) }
    }

    @Test
    fun `kan behandle samme vedtak flere ganger`() {
        val utbetalingId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()
        sendUtbetaling(utbetalingId = utbetalingId)
        sendVedtakFattet(utbetalingId = utbetalingId, vedtaksperiodeId = vedtaksperiodeId)
        val vedtakFattetRad = vedtakFattetDao.finn(utbetalingId)
        assertNotNull(vedtakFattetRad)
        assertTrue(vedtakFattetRad!!.erJournalført())
        sendVedtakFattet(utbetalingId = utbetalingId, vedtaksperiodeId = vedtaksperiodeId)
    }

    @Test
    fun `behandler ikke vedtak_fattet for AUU`() {
        val meldingId = UUID.randomUUID()
        testRapid.sendTestMessage(vedtakFattetForAvsluttetUtenUtbetaling(meldingId))
        harIkkeLagretTilDuplikattabellen(meldingId)
    }

    @Language("json")
    private fun vedtakFattetForAvsluttetUtenUtbetaling(hendelseId: UUID) = """
        {
            "@event_name": "vedtak_fattet",
            "fødselsnummer": "12345678910",
            "aktørId": "1000000000123",
            "vedtaksperiodeId": "94404185-be85-454f-b63d-8c6b57d90cf4",
            "behandlingId": "94404185-2288-4696-bf66-8c6b57d90cf4",
            "organisasjonsnummer": "979232535",
            "fom": "2024-08-20",
            "tom": "2024-09-01",
            "skjæringstidspunkt": "2024-08-20",
            "hendelser": [
                "94404185-de54-4ea6-a372-8c6b57d90cf4",
                "94404185-7352-43a1-a130-8c6b57d90cf4",
                "94404185-a2b9-4389-90ee-8c6b57d90cf4",
                "94404185-a06c-461b-b4ec-8c6b57d90cf4"
            ],
            "sykepengegrunnlag": 0.0,
            "grunnlagForSykepengegrunnlag": 0.0,
            "grunnlagForSykepengegrunnlagPerArbeidsgiver": {},
            "begrensning": "VET_IKKE",
            "inntekt": 0.0,
            "vedtakFattetTidspunkt": "2024-10-01T15:05:24.123456789",
            "begrunnelser": [],
            "tags": [
                "Førstegangsbehandling",
                "Avslag",
                "IngenUtbetaling",
                "EnArbeidsgiver"
            ],
            "@id": "$hendelseId",
            "@opprettet": "2024-10-01T15:05:24.123456789",
            "@forårsaket_av": {
                "id": "94404185-0222-48f0-befb-8c6b57d90cf4",
                "opprettet": "2024-10-01T15:05:23.123456789",
                "event_name": "avsluttet_uten_vedtak"
            }
        }
    """.trimIndent()

}
