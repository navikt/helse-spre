package no.nav.helse.spre.forsikringsoppgaver

import java.time.LocalDate
import java.util.UUID

class TestOppgaveClient: OppgaveoppretterClient {
    internal var oppgaveParams: OppgaveParams? = null
        private set

    override fun lagOppgave(
        duplikatkontrollId: UUID,
        fødselsnummer: String,
        årsak: Årsak,
        skjæringstidspunkt: LocalDate
    ) {
        oppgaveParams = OppgaveParams(fødselsnummer = fødselsnummer, årsak = årsak, skjæringstidspunkt = skjæringstidspunkt, duplikatkontrollId = duplikatkontrollId)
    }
}
