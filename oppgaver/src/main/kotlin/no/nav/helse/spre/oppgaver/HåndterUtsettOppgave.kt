package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import java.util.*

class HåndterUtsettOppgave(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    publisist: Publisist
) : River.PacketListener {

    private val observer = OppgaveObserver(oppgaveDAO, publisist, rapidsConnection)

    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("hendelse") }
            validate { it.requireValue("@event_name", "utsett_oppgave") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val hendelseId = UUID.fromString(packet["hendelse"].asText())
        val oppgave = oppgaveDAO.finnOppgave(hendelseId, observer) ?: return
        withMDC(mapOf("event" to "utsett_oppgave")) {
            Hendelse.Lest.accept(oppgave)
            log.info("Mottok utsett_oppgave-event: {}", oppgave.hendelseId)
        }
    }
}
