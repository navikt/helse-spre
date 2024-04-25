package no.nav.helse.spre.styringsinfo.teamsak.enhet

internal sealed interface Enhet

internal class FunnetEnhet(internal val id: String): Enhet {
    init { check(id.isNotBlank()) { "id på enhet kan ikke være tom." } }
}

internal object ManglendeEnhet: Enhet
internal object AutomatiskEnhet: Enhet