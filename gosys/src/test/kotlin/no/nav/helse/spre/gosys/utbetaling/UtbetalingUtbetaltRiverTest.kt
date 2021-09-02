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
        UtbetalingUtbetaltRiver(testRapid, utbetalingDao, vedtakFattetDao, duplikatsjekkDao, vedtakMediator)
    }

    @Test
    fun `Lagrer utbetaling utbetalt`() {
        val utbetalingId = UUID.randomUUID()
        sendUtbetaling(utbetalingId = utbetalingId)
        assertNotNull(utbetalingDao.finnUtbetalingData(utbetalingId))
    }
}
