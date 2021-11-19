package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.*

class HåndterVedtaksperiodeendringer(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    oppgaveProducers: List<OppgaveProducer>
) : River.PacketListener {

    private val observer = OppgaveObserver(oppgaveDAO, oppgaveProducers, rapidsConnection)

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
                log.info("fant oppgave: ${objectMapper.writeValueAsString(oppgave)}")
                val erSøknad = oppgave.dokumentType == DokumentType.Søknad

                when (gjeldendeTilstand) {
                    "TIL_INFOTRYGD" -> Hendelse.TilInfotrygd
                    "AVSLUTTET" -> Hendelse.Avsluttet
                    "AVSLUTTET_UTEN_UTBETALING" -> {
                        if (erSøknad) Hendelse.AvsluttetUtenUtbetaling
                        else Hendelse.MottattInntektsmeldingIAvsluttetUtenUtbetaling
                    }
                    else -> Hendelse.Lest
                }.accept(oppgave)
            }
    }
}

