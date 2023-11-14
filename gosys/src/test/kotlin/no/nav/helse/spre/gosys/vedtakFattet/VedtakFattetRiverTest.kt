package no.nav.helse.spre.gosys.vedtakFattet

import no.nav.helse.spre.gosys.e2e.AbstractE2ETest
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtbetaltRiver
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

internal class VedtakFattetRiverTest : AbstractE2ETest() {

    val vedtakFattetDao = VedtakFattetDao(dataSource)
    val utbetalingDao = UtbetalingDao(dataSource)


    init {
        VedtakFattetRiver(testRapid, vedtakFattetDao, utbetalingDao, duplikatsjekkDao, vedtakMediator)
        UtbetalingUtbetaltRiver(testRapid, utbetalingDao, vedtakFattetDao, duplikatsjekkDao, vedtakMediator)
    }

    @Test
    fun `Lagrer vedtak fattet`() {
        val utbetalingId = UUID.randomUUID()
        testRapid.sendTestMessage(vedtakFattetMedUtbetaling(utbetalingId = utbetalingId))
        assertNotNull(vedtakFattetDao.finnVedtakFattetData(utbetalingId))
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
        sendUtbetaling(utbetalingId = utbetalingId, vedtaksperiodeIder = listOf(vedtaksperiodeId))
        sendVedtakFattet(utbetalingId = utbetalingId, vedtaksperiodeId = vedtaksperiodeId)
        sendVedtakFattet(utbetalingId = utbetalingId, vedtaksperiodeId = vedtaksperiodeId)
        assertEquals(1, vedtakFattetDao.finnVedtakFattetData(utbetalingId).size)
    }

    @Language("json")
    private fun vedtakFattetMedUtbetaling(
        id: UUID = UUID.randomUUID(),
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        utbetalingId: UUID = UUID.randomUUID()
    ) = """{
  "@id": "$id",
  "vedtaksperiodeId": "$vedtaksperiodeId",
  "fødselsnummer": "12345678910",
  "utbetalingId": "$utbetalingId",
  "@event_name": "vedtak_fattet",
  "@opprettet": "2021-05-25T13:12:24.922420993",
  "fom": "2021-05-03",
  "tom": "2021-05-16",
  "hendelser": [
    "65ca68fa-0f12-40f3-ac34-141fa77c4270",
    "6977170d-5a99-4e7f-8d5f-93bda94a9ba3",
    "15aa9c84-a9cc-4787-b82a-d5447aa3fab1"
  ],
  "skjæringstidspunkt": "2021-01-07",
  "sykepengegrunnlag": 565260.0,
  "grunnlagForSykepengegrunnlagPerArbeidsgiver": {
    "123456789": 1234.56,
    "987654321": 6543.21
  },
  "inntekt": 47105.0,
  "aktørId": "9000011921123",
  "organisasjonsnummer": "123456789",
  "system_read_count": 0
}
    """

}
