package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import java.util.*

class VedtaksperiodeForkastetRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    publisist: Publisist,
) : River.PacketListener {

    private val observer = OppgaveObserver(oppgaveDAO, publisist, rapidsConnection)

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "vedtaksperiode_forkastet") }
            validate { it.requireKey("hendelser", "harPeriodeInnenfor16Dager", "forlengerPeriode", "fødselsnummer", "organisasjonsnummer") }
            validate { it.rejectValue("@forårsaket_av.event_name", "person_påminnelse") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        loggUkjentMelding("vedtaksperiode_forkastet", problems)
    }
    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val harPeriodeInnenfor16Dager = packet["harPeriodeInnenfor16Dager"].asBoolean()
        val forlengerPeriode = packet["forlengerPeriode"].asBoolean()
        val orgnummer = packet["organisasjonsnummer"].asText()
        val fødselsnummer = packet["fødselsnummer"].asText()
        val speilRelatert = harPeriodeInnenfor16Dager || forlengerPeriode

        val hendelser = packet["hendelser"]
            .map { UUID.fromString(it.asText()) }
        val oppgaver = hendelser.mapNotNull { oppgaveDAO.finnOppgave(it, observer) } + oppgaveDAO.finnOppgaverIDokumentOppdaget(orgnummer, fødselsnummer, observer, hendelser)
        oppgaver.forEach { oppgave ->
            withMDC(mapOf("event" to "vedtaksperiode_forkastet", "harPeriodeInnenfor16Dager" to harPeriodeInnenfor16Dager.utfall(), "forlengerPeriode" to forlengerPeriode.utfall())) {
                if (speilRelatert) oppgave.lagOppgavePåSpeilKø()
                else oppgave.lagOppgave()
            }
        }
    }

    private fun Boolean.utfall() = if (this) "JA" else "NEI"
}
