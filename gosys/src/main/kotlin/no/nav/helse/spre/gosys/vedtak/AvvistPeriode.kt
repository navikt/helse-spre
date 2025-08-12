package no.nav.helse.spre.gosys.vedtak

import java.time.LocalDate

data class AvvistPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
    val type: String,
    val begrunnelser: List<String>
) {
    fun kanUtvidesMed(other: AvvistPeriode): Boolean {
        if (!etterfølgerUtenGap(this, other)) return false
        return erLiktBegrunnet(this, other) || nesteErFridagEtterArbeidsdag(this, other)
    }

    private fun etterfølgerUtenGap(
        sisteInnslag: AvvistPeriode,
        avvistDag: AvvistPeriode,
    ) = sisteInnslag.tom.plusDays(1) == avvistDag.fom

    private fun nesteErFridagEtterArbeidsdag(
        sisteInnslag: AvvistPeriode,
        avvistDag: AvvistPeriode,
    ) = sisteInnslag.type == "Arbeidsdag" && avvistDag.type in listOf("Fridag", "Feriedag", "Permisjonsdag")

    private fun erLiktBegrunnet(
        sisteInnslag: AvvistPeriode,
        avvistDag: AvvistPeriode,
    ) = sisteInnslag.type == avvistDag.type
        && sisteInnslag.begrunnelser.containsAll(avvistDag.begrunnelser)
        && avvistDag.begrunnelser.containsAll(sisteInnslag.begrunnelser)
}

internal fun Iterable<AvvistPeriode>.slåSammenLikePerioder(): List<AvvistPeriode> = this
    .fold(listOf()) { akkumulator, avvistPeriode ->
        val siste = akkumulator.lastOrNull()
        val nySiste = when {
            // listen er tom
            siste == null -> listOf(avvistPeriode)
            // kan utvide <siste>
            siste.kanUtvidesMed(avvistPeriode) -> listOf(siste.copy(tom = avvistPeriode.tom))
            // må legge <siste> tilbake igjen, og lage ny fordi <siste> ikke kan utvides
            else -> listOf(siste, avvistPeriode)
        }

        akkumulator.dropLast(1) + nySiste
    }
