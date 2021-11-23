package no.nav.helse.spre.gosys.utbetaling

import java.util.*
import no.nav.helse.spre.gosys.e2e.AbstractE2ETest
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import no.nav.helse.spre.testhelpers.feriedager
import no.nav.helse.spre.testhelpers.fridager
import no.nav.helse.spre.testhelpers.januar
import no.nav.helse.spre.testhelpers.permisjonsdager
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

internal class UtbetalingUtenUtbetalingRiverTest : AbstractE2ETest() {

    val utbetalingDao = UtbetalingDao(dataSource)
    val vedtakFattetDao = VedtakFattetDao(dataSource)

    init {
        UtbetalingUtenUtbetalingRiver(testRapid, utbetalingDao, vedtakFattetDao, duplikatsjekkDao, vedtakMediator)
    }

    @Test
    fun `Lagrer utbetaling uten utbetaling`() {
        val utbetalingId = UUID.randomUUID()
        sendUtbetaling(
            utbetalingId = utbetalingId,
            sykdomstidslinje = fridager(1.januar, 10.januar)
                    + feriedager(11.januar, 16.januar)
                    + permisjonsdager(17.januar, 31.januar)
        )
        assertNotNull(utbetalingDao.finnUtbetalingData(utbetalingId))
    }
}
