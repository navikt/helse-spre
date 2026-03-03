package no.nav.helse.spre.forsikringsoppgaver

interface ForsikringsgrunnlagClient {
    fun forsikringsgrunnlag(behandlingId: BehandlingId): Forsikringsgrunnlag?
}
