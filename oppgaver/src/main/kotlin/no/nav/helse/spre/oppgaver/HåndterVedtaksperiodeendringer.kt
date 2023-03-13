package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
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
            validate { it.interestedIn("forrigeTilstand") }
        }.register(this)
    }


    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val forrigeTilstand = packet["forrigeTilstand"].asText()
        val gjeldendeTilstand = packet["gjeldendeTilstand"].asText()
        packet["hendelser"]
            .map { UUID.fromString(it.asText()) }
            .mapNotNull { oppgaveDAO.finnOppgave(it, observer) }
            .forEach { oppgave ->
                withMDC(mapOf("event" to "vedtaksperiode_endret", "tilstand" to gjeldendeTilstand, "forrigeTilstand" to forrigeTilstand)) {
                    when (gjeldendeTilstand) {
                        "AVVENTER_GODKJENNING", "AVVENTER_GODKJENNING_REVURDERING" -> oppgave.håndter(Hendelse.AvventerGodkjenning)
                        // Når noe går til Infotrygd sender Spleis ut signal om "opprett_oppgave" eller "opprett_oppgave_for_speilsaksbehandlere"
                        // derfor sendes det ikke noe signal om å opprette oppgave her
                        "TIL_INFOTRYGD" -> oppgaveDAO.lagreVedtaksperiodeEndretTilInfotrygd(oppgave.hendelseId)
                        "AVSLUTTET" -> oppgave.håndter(Hendelse.Avsluttet)
                        "AVSLUTTET_UTEN_UTBETALING" -> oppgave.håndter(Hendelse.AvsluttetUtenUtbetaling)
                        else -> {
                            oppgave.håndterLest()
                        }
                    }
                }
            }
    }
}

