package no.nav.helse.spre.forsikringsoppgaver

import java.time.LocalDate
import java.util.UUID

internal class OppgaveParams(
    val duplikatkontrollId: UUID,
    val fødselsnummer: String,
    val årsak: Årsak,
    val skjæringstidspunkt: LocalDate
)
