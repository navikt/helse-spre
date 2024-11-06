package no.nav.helse.spre.oppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import java.util.*

class InntektsmeldingIkkeHåndtertRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    private val publisist: Publisist,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "inntektsmelding_ikke_håndtert") }
            validate { it.requireKey("inntektsmeldingId", "harPeriodeInnenfor16Dager") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        loggUkjentMelding("inntektsmelding_ikke_håndtert", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        val inntektsmeldingId = packet["inntektsmeldingId"].asText().let { UUID.fromString(it) }
        val harPeriodeInnenfor16Dager = packet["harPeriodeInnenfor16Dager"].asBoolean()

        val speilRelatert = harPeriodeInnenfor16Dager

        val oppgave = oppgaveDAO.finnOppgave(inntektsmeldingId, observer) ?: return
        withMDC(mapOf("event" to "inntektsmelding_ikke_håndtert", "harPeriodeInnenfor16Dager" to harPeriodeInnenfor16Dager.utfall())) {
            oppgave.håndterInntektsmeldingIkkeHåndtert(speilRelatert)
            log.info("Mottok inntektsmelding_ikke_håndtert-event: {}", oppgave.hendelseId)
        }
    }

    private fun Boolean.utfall() = if (this) "JA" else "NEI"

}

