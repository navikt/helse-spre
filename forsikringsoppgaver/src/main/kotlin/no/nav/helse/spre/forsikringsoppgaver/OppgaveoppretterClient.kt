package no.nav.helse.spre.forsikringsoppgaver

import java.util.UUID

interface OppgaveoppretterClient {
    fun lagOppgave(gosysOppgaveId: UUID, fødselsnummer: String, årsak: Årsak)
    fun finnesDetOppgaveFor(gosysOppgaveId: UUID): Boolean
}
