package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import java.time.LocalDateTime
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
            validate { it.requireKey("hendelser", "harPeriodeInnenfor16Dager", "forlengerPeriode", "fødselsnummer", "organisasjonsnummer") }
            validate { it.interestedIn("tilstand", "@opprettet" )}
        }.register(this)
    }

    private val sisteMelding = LocalDateTime.parse("2023-05-04T17:12:49.343741751")
    private fun JsonMessage.auuUtbetaltIInfotrygd(): Boolean {
        if (get("tilstand").asText() != "AVSLUTTET_UTEN_UTBETALING") return false
        val auuUtbetaltIInfotrygd = get("@opprettet").asLocalDateTime() <= sisteMelding
        if (auuUtbetaltIInfotrygd) sikkerLog.info("Dropper å lage oppgave. auuUtbetaltIInfotrygd:\n\t$${toJson()}")
        return auuUtbetaltIInfotrygd
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (packet.auuUtbetaltIInfotrygd()) return
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

