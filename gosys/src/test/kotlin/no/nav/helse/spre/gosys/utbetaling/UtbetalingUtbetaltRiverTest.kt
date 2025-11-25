package no.nav.helse.spre.gosys.utbetaling

import java.util.*
import no.nav.helse.spre.gosys.e2e.AbstractE2ETest
import no.nav.helse.spre.testhelpers.feriedager
import no.nav.helse.spre.testhelpers.januar
import no.nav.helse.spre.testhelpers.permisjonsdager
import no.nav.helse.spre.testhelpers.utbetalingsdager
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class UtbetalingUtbetaltRiverTest : AbstractE2ETest() {

    @Test
    fun `Lagrer utbetaling utbetalt`() {
        val utbetalingId = UUID.randomUUID()
        sendUtbetaling(
            utbetalingId = utbetalingId,
            sykdomstidslinje = utbetalingsdager(1.januar, 20.januar)
                + feriedager(21.januar, 26.januar)
                + permisjonsdager(27.januar, 31.januar)
        )

        sessionFactory.transactionally { assertNotNull(utbetalingDao.finnUtbetalingData(utbetalingId)) }
    }
}
