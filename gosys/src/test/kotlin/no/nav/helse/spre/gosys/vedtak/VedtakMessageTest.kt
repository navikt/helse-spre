package no.nav.helse.spre.gosys.vedtak

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class VedtakMessageTest {
    @Test
    fun `grupperer ikke-utbetalte dager basert på type`() {
        val json =
            listOf(
                avvistPeriode(LocalDate.of(2020, 7, 20), "Fridag", emptyList()),
                avvistPeriode(LocalDate.of(2020, 7, 21), "Fridag", emptyList()),
                avvistPeriode(LocalDate.of(2020, 7, 22), "Fridag", emptyList()),
                avvistPeriode(LocalDate.of(2020, 7, 23), "Fridag", emptyList()),
                avvistPeriode(LocalDate.of(2020, 7, 24), "Fridag", emptyList()),
                avvistPeriode(LocalDate.of(2020, 7, 25), "Fridag", emptyList()),
                avvistPeriode(LocalDate.of(2020, 7, 26), "AvvistDag", listOf("ManglerOpptjening", "MinimumSykdomsgrad")),
                avvistPeriode(LocalDate.of(2020, 7, 27), "AvvistDag", listOf("ManglerOpptjening")),
                avvistPeriode(LocalDate.of(2020, 7, 28), "AvvistDag", listOf("ManglerOpptjening")),
                avvistPeriode(LocalDate.of(2020, 7, 29), "Fridag", emptyList()),
                avvistPeriode(LocalDate.of(2020, 7, 30), "Fridag", emptyList()),
                avvistPeriode(LocalDate.of(2020, 7, 31), "Fridag", emptyList()),
                avvistPeriode(LocalDate.of(2020, 8, 4), "AvvistDag", listOf("ManglerOpptjening")),
                avvistPeriode(LocalDate.of(2020, 8, 5), "AvvistDag", listOf("ManglerOpptjening", "SykepengedagerOppbrukt")),
                avvistPeriode(LocalDate.of(2020, 8, 6), "AvvistDag", listOf("ManglerOpptjening", "SykepengedagerOppbrukt"))
            ).slåSammenLikePerioder()

        assertEquals(
            listOf(
                AvvistPeriode(LocalDate.of(2020, 7, 20), LocalDate.of(2020, 7, 25), "Fridag", emptyList()),
                AvvistPeriode(LocalDate.of(2020, 7, 26), LocalDate.of(2020, 7, 26), "AvvistDag", listOf("ManglerOpptjening", "MinimumSykdomsgrad")),
                AvvistPeriode(LocalDate.of(2020, 7, 27), LocalDate.of(2020, 7, 28), "AvvistDag", listOf("ManglerOpptjening")),
                AvvistPeriode(LocalDate.of(2020, 7, 29), LocalDate.of(2020, 7, 31), "Fridag", emptyList()),
                AvvistPeriode(LocalDate.of(2020, 8, 4), LocalDate.of(2020, 8, 4), "AvvistDag", listOf("ManglerOpptjening")),
                AvvistPeriode(LocalDate.of(2020, 8, 5), LocalDate.of(2020, 8, 6), "AvvistDag", listOf("ManglerOpptjening", "SykepengedagerOppbrukt"))
            ), json
        )
    }

    private fun avvistPeriode(fom: LocalDate, type: String, begrunnelser: List<String>) = AvvistPeriode(
        fom = fom,
        tom = fom,
        type = type,
        begrunnelser = begrunnelser
    )
}
