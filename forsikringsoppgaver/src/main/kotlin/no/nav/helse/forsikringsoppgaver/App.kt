package no.nav.helse.forsikringsoppgaver

import java.util.UUID
import no.nav.helse.rapids_rivers.RapidApplication

fun main() {
    val rapidApp = RapidApplication.create(System.getenv())
    val oppgaveClient =
        object : OppgaveoppretterClient {
            override fun lagOppgave(gosysOppgaveId: UUID, fødselsnummer: String, årsak: Årsak) {}
            override fun finnesDetOppgaveFor(gosysOppgaveId: UUID): Boolean {
                TODO("Not yet implemented")
            }
        }
    val forsikringsgrunnlagClient =
        object : ForsikringsgrunnlagClient {
            override fun forsikringsgrunnlag(behandlingId: BehandlingId): Forsikringsgrunnlag? {
                TODO("Not yet implemented")
            }
        }
    SelvstendigUtbetaltEtterVentetidRiver(rapidApp, oppgaveClient, forsikringsgrunnlagClient)
    SelvstendigIngenDagerIgjenRiver(rapidApp, oppgaveClient, forsikringsgrunnlagClient)
    rapidApp.start()
}
