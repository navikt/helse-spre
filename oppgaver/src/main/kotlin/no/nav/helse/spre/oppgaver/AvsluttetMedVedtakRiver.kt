package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import java.util.*

class AvsluttetMedVedtakRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    publisist: Publisist,
) : River.PacketListener {

    private val observer = OppgaveObserver(oppgaveDAO, publisist, rapidsConnection)

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "avsluttet_med_vedtak") }
            validate { it.requireKey("hendelser") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        loggUkjentMelding("avsluttet_med_vedtak", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        withMDC(mapOf("event" to "avsluttet_med_vedtak")) {
            packet["hendelser"]
                .map { UUID.fromString(it.asText()) }
                .mapNotNull { oppgaveDAO.finnOppgave(it, observer) }
                .forEach { oppgave ->
                    oppgave.håndter(Hendelse.Avsluttet)
                }
        }
    }
}

