package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import java.util.*

class HåndterOpprettOppgaveForSpeilsaksbehandlere(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    publisist: Publisist,
) : River.PacketListener {

    private val observer = OppgaveObserver(oppgaveDAO, publisist, rapidsConnection)

    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("hendelser") }
            validate { it.requireValue("@event_name", "opprett_oppgave_for_speilsaksbehandlere") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        packet["hendelser"]
            .map { UUID.fromString(it.asText()) }
            .mapNotNull { oppgaveDAO.finnOppgave(it, observer) }
            .forEach { oppgave ->
                withMDC(mapOf("event" to "opprett_oppgave_for_speilsaksbehandlere")) {
                    oppgave.håndter(Hendelse.AvbruttOgHarRelatertUtbetaling)
                    log.info("Mottok opprett_oppgave_for_speilsaksbehandlere-event : {}", oppgave.hendelseId)
                }
            }
    }
}
