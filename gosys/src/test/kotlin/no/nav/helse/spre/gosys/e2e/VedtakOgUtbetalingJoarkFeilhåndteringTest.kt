package no.nav.helse.spre.gosys.e2e

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

internal class VedtakOgUtbetalingJoarkFeilhåndteringTest : AbstractE2ETest() {

    @Test
    fun `gir opp og lar appen restarte om Joark-kallet feiler`() {
        val utbetalingId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()
        assertThrows<IllegalStateException> {
            sendUtbetaling(utbetalingId = utbetalingId)
            sendVedtakFattet(utbetalingId = utbetalingId, vedtaksperiodeId = vedtaksperiodeId)
        }
        assertFalse(harLagretTilDuplikattabellen(vedtaksperiodeId))
        assertFalse(harLagretTilDuplikattabellen(utbetalingId))
    }

    override fun MockRequestHandleScope.handlerForJoark(request: HttpRequestData): HttpResponseData {
        capturedJoarkRequests.add(request)
        error("connection reset")
    }

}
