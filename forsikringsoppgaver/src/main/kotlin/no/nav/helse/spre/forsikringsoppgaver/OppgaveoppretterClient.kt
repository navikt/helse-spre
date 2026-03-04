package no.nav.helse.spre.forsikringsoppgaver

import java.util.*

interface OppgaveoppretterClient {
    fun lagOppgave(duplikatkontrollId: UUID, fødselsnummer: String, årsak: Årsak)
}
