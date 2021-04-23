package no.nav.helse.spre.saksbehandlingsstatistikk

import java.util.*

class UtbetalingUtbetaltData(
    val utbetalingId: UUID,
    val type: String,
)

/*JsonMessage.newMessage(mapOf(
    "utbetalingId" to event.utbetalingId,
    "type" to event.type,
    "fom" to event.fom,
    "tom" to event.tom,
    "maksdato" to event.maksdato,
    "forbrukteSykedager" to event.forbrukteSykedager,
    "gjenståendeSykedager" to event.gjenståendeSykedager,
    "ident" to event.ident,
    "epost" to event.epost,
    "tidspunkt" to event.tidspunkt,
    "automatiskBehandling" to event.automatiskBehandling,
    "arbeidsgiverOppdrag" to event.arbeidsgiverOppdrag,
    "personOppdrag" to event.personOppdrag,
    "utbetalingsdager" to event.utbetalingsdager
))*/
