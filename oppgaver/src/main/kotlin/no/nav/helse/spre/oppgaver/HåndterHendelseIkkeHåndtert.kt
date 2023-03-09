package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.*

class HåndterHendelseIkkeHåndtert(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    publisist: Publisist,
) : River.PacketListener {

    private val observer = OppgaveObserver(oppgaveDAO, publisist, rapidsConnection)

    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("hendelseId") }
            validate { it.requireValue("@event_name", "hendelse_ikke_håndtert") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        UUID.fromString(packet["hendelseId"].asText()).let { hendelseId ->
            oppgaveDAO.finnOppgave(hendelseId, observer)?.let { oppgave ->
                Hendelse.TilInfotrygd.accept(oppgave)
                log.info("Oppgave på hendelseId: {} av type: {} med dokumentId: {}, fører til oppgaveopprettelse",
                    hendelseId,
                    oppgave.dokumentType,
                    oppgave.dokumentId
                )
            } ?: log.info("Mottok hendelse_ikke_håndtert-event: {}, men vi har ikke en tilhørende søknad",
                 hendelseId
            )
        }
    }
}

