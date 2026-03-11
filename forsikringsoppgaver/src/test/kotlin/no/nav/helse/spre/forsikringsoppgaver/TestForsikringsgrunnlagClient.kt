package no.nav.helse.spre.forsikringsoppgaver

class TestForsikringsgrunnlagClient: ForsikringsgrunnlagClient {
    internal var forsikringsgrunnlag: Forsikringsgrunnlag? = null

    override fun forsikringsgrunnlag(behandlingId: BehandlingId): Forsikringsgrunnlag? = forsikringsgrunnlag
}
