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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

internal class VedtakOgUtbetalingJoarkFeilhåndteringTest : AbstractE2ETest() {

    private val vedtakFattetDao = VedtakFattetDao(dataSource)
    private val utbetalingDao = UtbetalingDao(dataSource)

    init {
        VedtakFattetRiver(testRapid, vedtakFattetDao, utbetalingDao, duplikatsjekkDao, vedtakMediator)
        UtbetalingUtbetaltRiver(testRapid, utbetalingDao, vedtakFattetDao, duplikatsjekkDao, vedtakMediator)
    }

    @Test
    fun `gir opp og lar appen restarte om Joark-kallet feiler`() {
        val utbetalingId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()
        assertThrows<IllegalStateException> {
            sendUtbetaling(utbetalingId = utbetalingId, vedtaksperiodeIder = listOf(vedtaksperiodeId))
            sendVedtakFattet(utbetalingId = utbetalingId, vedtaksperiodeId = vedtaksperiodeId)
        }
        assertFalse(harLagretTilDuplikattabellen(vedtaksperiodeId))
        assertFalse(harLagretTilDuplikattabellen(utbetalingId))
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

}
