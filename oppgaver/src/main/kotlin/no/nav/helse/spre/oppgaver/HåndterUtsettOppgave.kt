package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
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
        val oppgave = oppgaveDAO.finnOppgave(hendelseId) ?: return
        oppgave.setObserver(observer)
        Hendelse.Lest.accept(oppgave)
        log.info("Mottok utsett_oppgave-event: {}", oppgave.hendelseId)
    }
}
