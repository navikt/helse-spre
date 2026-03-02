package no.nav.helse.forsikringsoppgaver

interface ForsikringsgrunnlagClient {
    fun forsikringsgrunnlag(behandlingId: BehandlingId): Forsikringsgrunnlag?
}
