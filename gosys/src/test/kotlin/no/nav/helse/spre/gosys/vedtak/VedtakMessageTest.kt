package no.nav.helse.spre.gosys.vedtak

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class VedtakMessageTest {
    @Test
    fun `grupperer ikke utbetalte dager basert på type`() {
        val json =
            listOf(
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 20), "Fridag"),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 21), "Fridag"),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 22), "Fridag"),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 23), "Fridag"),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 24), "Fridag"),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 25), "Fridag"),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 26), "MinimumSykdomsgrad"),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 27), "MinimumSykdomsgrad"),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 28), "MinimumSykdomsgrad"),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 29), "Fridag"),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 30), "Fridag"),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 31), "Fridag"),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 8, 4), "SykepengedagerOppbrukt"),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 8, 5), "SykepengedagerOppbrukt"),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 8, 6), "SykepengedagerOppbrukt")
            ).settSammenIkkeUtbetalteDager()

        assertEquals(
            listOf(
                VedtakMessage.DagAcc(LocalDate.of(2020, 7, 20), LocalDate.of(2020, 7, 25), "Fridag"),
                VedtakMessage.DagAcc(LocalDate.of(2020, 7, 26), LocalDate.of(2020, 7, 28), "MinimumSykdomsgrad"),
                VedtakMessage.DagAcc(LocalDate.of(2020, 7, 29), LocalDate.of(2020, 7, 31), "Fridag"),
                VedtakMessage.DagAcc(LocalDate.of(2020, 8, 4), LocalDate.of(2020, 8, 6), "SykepengedagerOppbrukt")
            ), json
        )
    }
}
