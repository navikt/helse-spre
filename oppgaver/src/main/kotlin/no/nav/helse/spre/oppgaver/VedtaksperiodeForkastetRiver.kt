package no.nav.helse.spre.oppgaver

import net.logstash.logback.argument.StructuredArguments.kv
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
            validate { it.requireKey("hendelser", "vedtaksperiodeId", "tilstand", "harPeriodeInnenfor16Dager", "forlengerPeriode", "fødselsnummer", "aktørId", "organisasjonsnummer") }
            validate { it.requireKey("behandletIInfotrygd") }
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
        val behandletIInfotrygd = packet["behandletIInfotrygd"].asBoolean(false)

        val hendelser = packet["hendelser"].map { UUID.fromString(it.asText()) }
        val oppgaver = hendelser.mapNotNull { oppgaveDAO.finnOppgave(it, observer) } + oppgaveDAO.finnOppgaverIDokumentOppdaget(orgnummer, fødselsnummer, observer, hendelser)

        withMDC(mapOf(
            "event" to "vedtaksperiode_forkastet",
            "harPeriodeInnenfor16Dager" to harPeriodeInnenfor16Dager.utfall(),
            "forlengerPeriode" to forlengerPeriode.utfall(),
            "behandletIInfotrygd" to behandletIInfotrygd.utfall(),
            "aktørId" to packet["aktørId"].asText(),
            "vedtaksperiodeId" to packet["vedtaksperiodeId"].asText(),
            "tilstand" to packet["tilstand"].asText()
        )) {
            if (oppgaver.isEmpty()) return@withMDC sikkerLog.info("Ignorerer vedtaksperiode_opprettet fordi ingen hendelser kan mappes til oppgaver i databasen", kv("fødselsnummer", fødselsnummer))
            if (behandletIInfotrygd) return@withMDC sikkerLog.info("Ignorerer vedtaksperiode_opprettet fordi perioden er allerede behandlet i Infotrygd", kv("fødselsnummer", fødselsnummer))
            oppgaver.forEach { oppgave ->
                if (speilRelatert) oppgave.lagOppgavePåSpeilKø()
                else oppgave.lagOppgave()
            }
        }
    }

    private fun Boolean.utfall() = if (this) "JA" else "NEI"
}

