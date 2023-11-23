package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*

class SlettPersonRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.requireValue("@event_name", "slett_person") }
            validate { it.requireKey("fødselsnummer") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Fjerner oppgaver knyttet til {}", packet["fødselsnummer"].asText())
        oppgaveDAO.fjernOpgaver(packet["fødselsnummer"].asText())
    }
}

