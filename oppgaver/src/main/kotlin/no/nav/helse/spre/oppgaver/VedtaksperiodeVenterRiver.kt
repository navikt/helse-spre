package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import java.util.*

class VedtaksperiodeVenterRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    publisist: Publisist,
) : River.PacketListener {

    private val observer = OppgaveObserver(oppgaveDAO, publisist, rapidsConnection)

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "vedtaksperiode_venter") }
            validate { it.demandAny("venterPå.venteårsak.hva", listOf("GODKJENNING", "SØKNAD", "INNTEKTSMELDING")) }
            validate { it.requireKey("hendelser", "@id", "organisasjonsnummer", "venterPå.organisasjonsnummer") }
            validate { it.interestedIn("venterPå.venteårsak.hvorfor") } // TODO: Fjern
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        loggUkjentMelding("vedtaksperiode_venter", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (packet["venterPå.venteårsak.hva"].asText() == "INNTEKTSMELDING") {
            val organisasjonsnummer = packet["organisasjonsnummer"].asText()
            val venterPåOrganisasjonsnummer = packet["venterPå.organisasjonsnummer"].asText()
            val hvorfor = packet["venterPå.venteårsak.hvorfor"].asText()
            if (hvorfor == "MANGLER_TILSTREKKELIG_INFORMASJON_TIL_UTBETALING_ANDRE_ARBEIDSGIVERE") håndter(packet) // TODO: Denne skal knertes. Når det gjøres kan nok dette løses i river validation
            if (organisasjonsnummer != venterPåOrganisasjonsnummer) håndter(packet) // Utsetter kun om vi venter på annen AG
        }
        else håndter(packet) // Når vi venter på GODKJENNING/SØKNAD så håndterer vi alltid
    }

    private fun håndter(packet: JsonMessage) {
        val vedtaksperiodeVenterId = packet["@id"].asText()
        packet["hendelser"]
            .map { UUID.fromString(it.asText()) }
            .mapNotNull { oppgaveDAO.finnOppgave(it, observer) }
            .forEach { oppgave ->
                withMDC(mapOf("event" to "vedtaksperiode_venter", "id" to vedtaksperiodeVenterId)) {
                    oppgave.håndter(Hendelse.VedtaksperiodeVenter)
                }
            }
    }
}