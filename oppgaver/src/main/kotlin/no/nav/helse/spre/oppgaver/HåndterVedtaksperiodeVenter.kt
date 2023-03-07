package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.util.*

class HåndterVedtaksperiodeVenter(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    publisist: Publisist,
) : River.PacketListener {

    private val observer = OppgaveObserver(oppgaveDAO, publisist, rapidsConnection)

    private companion object {
        val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate { it.requireValue("@event_name", "vedtaksperiode_venter") }
            validate { it.requireKey("hendelser", "@id") }
            validate { it.demandValue("venterPå.venteårsak.hva", "GODKJENNING") }
        }.register(this)
    }


    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtaksperiodeVenterId = packet["@id"].asText()
        packet["hendelser"]
            .map { UUID.fromString(it.asText()) }
            .mapNotNull { oppgaveDAO.finnOppgave(it) }
            .onEach { it.setObserver(observer) }
            .forEach { oppgave ->
                withMDC(mapOf("event" to "vedtaksperiode_venter", "id" to vedtaksperiodeVenterId)) {
                    sikkerlogg.info("Ville utsatt hendelseId ${oppgave.hendelseId} for ${oppgave.dokumentType}")
                }
            }
    }
}

