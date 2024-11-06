package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import java.util.*

class VedtaksperiodeEndretRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    private val publisist: Publisist,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "vedtaksperiode_endret") }
            validate { it.requireKey("hendelser") }
            validate { it.interestedIn("forrigeTilstand") }
            validate { it.demandAny("gjeldendeTilstand", listOf("AVVENTER_GODKJENNING", "AVVENTER_GODKJENNING_REVURDERING"))}
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        loggUkjentMelding("vedtaksperiode_endret", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        val forrigeTilstand = packet["forrigeTilstand"].asText()
        val gjeldendeTilstand = packet["gjeldendeTilstand"].asText()
        packet["hendelser"]
            .map { UUID.fromString(it.asText()) }
            .mapNotNull { oppgaveDAO.finnOppgave(it, observer) }
            .forEach { oppgave ->
                withMDC(mapOf("event" to "vedtaksperiode_endret", "tilstand" to gjeldendeTilstand, "forrigeTilstand" to forrigeTilstand)) {
                    oppgave.håndter(Hendelse.AvventerGodkjenning)
                }
            }
    }
}

