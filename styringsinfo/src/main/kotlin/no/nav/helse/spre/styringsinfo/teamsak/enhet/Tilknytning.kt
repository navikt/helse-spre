package no.nav.helse.spre.styringsinfo.teamsak.enhet

internal sealed interface Tilknytning

internal data class FunnetTilknytning(internal val enhet: String, internal val avdeling: String): Tilknytning {
    init {
        check(enhet.isNotBlank()) { "enhet på Tilknytning kan ikke være tom." }
        check(avdeling.isNotBlank()) { "avdeling på Tilknytning kan ikke være tom." }
    }
}

internal object ManglendeTilknytning: Tilknytning
internal object AutomatiskTilknytning: Tilknytning
