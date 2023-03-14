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
            validate {it.requireKey("hendelser", "harPeriodeInnenfor16Dager", "forlengerPeriode", "fødselsnummer", "organisasjonsnummer")}
        }.register(this)
    }


    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val harPeriodeInnenfor16Dager = packet["harPeriodeInnenfor16Dager"].asBoolean()
        val forlengerPeriode = packet["forlengerPeriode"].asBoolean()
        val orgnummer = packet["organisasjonsnummer"].asText()
        val fødselsnummer = packet["fødselsnummer"].asText()

        packet["hendelser"]
            .map { UUID.fromString(it.asText()) }
            .mapNotNull { oppgaveDAO.finnOppgave(it, observer) }
            .forEach { oppgave ->
                withMDC(mapOf("event" to "vedtaksperiode_forkastet", "harPeriodeInnenfor16Dager" to harPeriodeInnenfor16Dager.utfall(), "forlengerPeriode" to forlengerPeriode.utfall())) {
                    if (harPeriodeInnenfor16Dager || forlengerPeriode) oppgave.lagOppgavePåSpeilKø()
                    else oppgave.lagOppgave()
                }
            }
    }

    private fun Boolean.utfall() = if (this) "JA" else "NEI"
}

