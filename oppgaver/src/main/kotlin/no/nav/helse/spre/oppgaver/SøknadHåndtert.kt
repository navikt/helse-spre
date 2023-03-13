package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import java.util.*

class SøknadHåndtert(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    publisist: Publisist,
) : River.PacketListener {

    private val observer = OppgaveObserver(oppgaveDAO, publisist, rapidsConnection)

    init {
        River(rapidsConnection).apply {
            validate { it.requireValue("@event_name", "søknad_håndtert") }
            validate { it.requireKey("søknadId") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet["søknadId"].asText().let { UUID.fromString(it) }
        val oppgave = oppgaveDAO.finnOppgave(søknadId, observer) ?: return
        withMDC(mapOf("event" to "søknad_håndtert")) {
            oppgave.håndterLest()
            log.info("Mottok søknad_håndtert-event: {}", oppgave.hendelseId)
        }
    }
}

