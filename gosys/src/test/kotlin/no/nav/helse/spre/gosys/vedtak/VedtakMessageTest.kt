package no.nav.helse.spre.gosys.vedtak

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class VedtakMessageTest {
    @Test
    fun `grupperer ikke-utbetalte dager basert på type`() {
        val json =
            listOf(
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 20), "Fridag", emptyList()),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 21), "Fridag", emptyList()),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 22), "Fridag", emptyList()),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 23), "Fridag", emptyList()),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 24), "Fridag", emptyList()),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 25), "Fridag", emptyList()),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 26), "AvvistDag", listOf("ManglerOpptjening", "MinimumSykdomsgrad")),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 27), "AvvistDag", listOf("ManglerOpptjening")),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 28), "AvvistDag", listOf("ManglerOpptjening")),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 29), "Fridag", emptyList()),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 30), "Fridag", emptyList()),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 7, 31), "Fridag", emptyList()),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 8, 4), "AvvistDag", listOf("ManglerOpptjening")),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 8, 5), "AvvistDag", listOf("ManglerOpptjening", "SykepengedagerOppbrukt")),
                VedtakMessage.IkkeUtbetaltDag(LocalDate.of(2020, 8, 6), "AvvistDag", listOf("ManglerOpptjening", "SykepengedagerOppbrukt"))
            ).settSammenIkkeUtbetalteDager()

        assertEquals(
            listOf(
                VedtakMessage.AvvistPeriode(LocalDate.of(2020, 7, 20), LocalDate.of(2020, 7, 25), "Fridag", emptyList()),
                VedtakMessage.AvvistPeriode(LocalDate.of(2020, 7, 26), LocalDate.of(2020, 7, 26), "AvvistDag", listOf("ManglerOpptjening", "MinimumSykdomsgrad")),
                VedtakMessage.AvvistPeriode(LocalDate.of(2020, 7, 27), LocalDate.of(2020, 7, 28), "AvvistDag", listOf("ManglerOpptjening")),
                VedtakMessage.AvvistPeriode(LocalDate.of(2020, 7, 29), LocalDate.of(2020, 7, 31), "Fridag", emptyList()),
                VedtakMessage.AvvistPeriode(LocalDate.of(2020, 8, 4), LocalDate.of(2020, 8, 4), "AvvistDag", listOf("ManglerOpptjening")),
                VedtakMessage.AvvistPeriode(LocalDate.of(2020, 8, 5), LocalDate.of(2020, 8, 6), "AvvistDag", listOf("ManglerOpptjening", "SykepengedagerOppbrukt"))
            ), json
        )
    }
}
