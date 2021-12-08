package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.*

class HåndterOpprettOppgaveForSpeilsaksbehandlere(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    oppgaveProducers: List<OppgaveProducer>
) : River.PacketListener {

    private val observer = OppgaveObserver(oppgaveDAO, oppgaveProducers, rapidsConnection)

    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("hendelser") }
            validate { it.requireValue("@event_name", "opprett_oppgave_for_speilsaksbehandlere") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        packet["hendelser"]
            .map { UUID.fromString(it.asText()) }
            .mapNotNull { oppgaveDAO.finnOppgave(it) }
            .onEach { it.setObserver(observer) }
            .forEach { oppgave ->
                Hendelse.AvbruttOgHarRelatertUtbetaling.accept(oppgave)
                log.info("Mottok opprett_oppgave_for_speilsaksbehandlere-event : {}",
                    oppgave.hendelseId)
            }
    }
}
