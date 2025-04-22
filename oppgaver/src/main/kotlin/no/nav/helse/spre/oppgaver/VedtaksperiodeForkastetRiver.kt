package no.nav.helse.spre.oppgaver

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import net.logstash.logback.argument.StructuredArguments.kv
import java.time.LocalDate
import java.util.*

class VedtaksperiodeForkastetRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    private val publisist: Publisist,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "vedtaksperiode_forkastet") }
            precondition { it.forbidValue("@forårsaket_av.event_name", "person_påminnelse") }
            validate { it.requireKey("hendelser", "vedtaksperiodeId", "tilstand", "harPeriodeInnenfor16Dager", "forlengerPeriode", "fødselsnummer", "organisasjonsnummer") }
            validate { it.require("fom", JsonNode::asLocalDate) }
            validate { it.require("tom", JsonNode::asLocalDate) }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        loggUkjentMelding("vedtaksperiode_forkastet", problems)
    }
    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        val harPeriodeInnenfor16Dager = packet["harPeriodeInnenfor16Dager"].asBoolean()
        val forlengerPeriode = packet["forlengerPeriode"].asBoolean()
        val orgnummer = packet["organisasjonsnummer"].asText()
        val fødselsnummer = packet["fødselsnummer"].asText()
        val speilRelatert = harPeriodeInnenfor16Dager || forlengerPeriode
        val fom = packet["fom"].asLocalDate()
        val tom = packet["tom"].asLocalDate()
        val erGammelPeriode = tom <= LocalDate.of(2022, 12, 31)
        val hendelser = packet["hendelser"].map { UUID.fromString(it.asText()) }
        val oppgaver = hendelser.mapNotNull { oppgaveDAO.finnOppgave(it, observer) } + oppgaveDAO.finnOppgaverIDokumentOppdaget(orgnummer, fødselsnummer, observer, hendelser)

        withMDC(mapOf(
            "event" to "vedtaksperiode_forkastet",
            "harPeriodeInnenfor16Dager" to harPeriodeInnenfor16Dager.utfall(),
            "forlengerPeriode" to forlengerPeriode.utfall(),
            "vedtaksperiodeId" to packet["vedtaksperiodeId"].asText(),
            "tilstand" to packet["tilstand"].asText(),
            "fomÅr" to fom.year.toString(),
            "tomÅr" to tom.year.toString(),
            "erGammelPeriode" to erGammelPeriode.utfall()
        )) {
            if (oppgaver.isEmpty()) return@withMDC sikkerLog.info("Ignorerer vedtaksperiode_forkastet fordi ingen hendelser kan mappes til oppgaver i databasen for {}", kv("fødselsnummer", fødselsnummer))
            oppgaver.forEach { oppgave ->
                if (speilRelatert) oppgave.lagOppgavePåSpeilKø()
                else oppgave.lagOppgave()
            }
        }
    }

    private fun Boolean.utfall() = if (this) "JA" else "NEI"
}

