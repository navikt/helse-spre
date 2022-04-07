package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.*

class HåndterVedtaksperiodeendringer(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    publisist: Publisist,
) : River.PacketListener {

    private val observer = OppgaveObserver(oppgaveDAO, publisist, rapidsConnection)

    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("gjeldendeTilstand", "hendelser") }
            validate { it.requireValue("@event_name", "vedtaksperiode_endret") }
        }.register(this)
    }


    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val gjeldendeTilstand = packet["gjeldendeTilstand"].asText()
        packet["hendelser"]
            .map { UUID.fromString(it.asText()) }
            .mapNotNull { oppgaveDAO.finnOppgave(it) }
            .onEach { it.setObserver(observer) }
            .forEach { oppgave ->
                when (gjeldendeTilstand) {
                    "AVVENTER_GODKJENNING" -> oppgave.forlengTimeout()
                    "TIL_INFOTRYGD" -> oppgaveDAO.lagreVedtaksperiodeEndretTilInfotrygd(oppgave.hendelseId)
                    "AVSLUTTET" -> Hendelse.Avsluttet.accept(oppgave)
                    "AVSLUTTET_UTEN_UTBETALING" -> Hendelse.AvsluttetUtenUtbetaling.accept(oppgave)
                    else -> Hendelse.Lest.accept(oppgave)
                }
            }
    }
}

