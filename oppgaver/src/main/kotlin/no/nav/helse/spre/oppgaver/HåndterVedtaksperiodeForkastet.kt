package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import java.util.*

class HåndterVedtaksperiodeForkastet(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    publisist: Publisist,
) : River.PacketListener {

    private val observer = OppgaveObserver(oppgaveDAO, publisist, rapidsConnection)

    init {
        River(rapidsConnection).apply {
            validate { it.requireValue("@event_name", "vedtaksperiode_forkastet") }
            validate {it.requireKey("hendelser", "harOverlappendeVedtaksperiode", "fødselsnummer", "organisasjonsnummer")}
        }.register(this)
    }


    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val harOverlappendeVedtaksperiode = packet["harOverlappendeVedtaksperiode"].asBoolean()
        val orgnummer = packet["organisasjonsnummer"].asText()
        val fødselsnummer = packet["fødselsnummer"].asText()

        packet["hendelser"]
            .map { UUID.fromString(it.asText()) }
            .mapNotNull { oppgaveDAO.finnOppgave(it, observer) }
            .forEach { oppgave ->
                withMDC(mapOf("event" to "vedtaksperiode_forkastet")) {
                    if (harOverlappendeVedtaksperiode) oppgave.lagOppgavePåSpeilKø()
                    else oppgave.lagOppgave()
                }
            }
    }
}

