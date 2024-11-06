package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import java.util.*

class SøknadHåndtertRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    private val publisist: Publisist,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "søknad_håndtert") }
            validate { it.requireKey("søknadId") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        loggUkjentMelding("søknad_håndtert", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        val søknadId = packet["søknadId"].asText().let { UUID.fromString(it) }
        val oppgave = oppgaveDAO.finnOppgave(søknadId, observer) ?: return
        withMDC(mapOf("event" to "søknad_håndtert")) {
            oppgave.håndterLest()
            log.info("Mottok søknad_håndtert-event: {}", oppgave.hendelseId)
        }
    }
}

