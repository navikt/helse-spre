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
            validate { it.demandValue("venterPå.venteårsak.hva", "GODKJENNING") }
            validate { it.requireKey("hendelser", "@id") }
        }.register(this)

        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "vedtaksperiode_venter") }
            validate {
                it.demandAny(
                    "venterPå.venteårsak.hvorfor",
                    listOf(
                        "MANGLER_REFUSJONSOPPLYSNINGER_PÅ_ANDRE_ARBEIDSGIVERE",
                        "MANGLER_INNTEKT_FOR_VILKÅRSPRØVING_PÅ_ANDRE_ARBEIDSGIVERE",
                        "MANGLER_TILSTREKKELIG_INFORMASJON_TIL_UTBETALING_ANDRE_ARBEIDSGIVERE",
                        "HAR_SYKMELDING_SOM_OVERLAPPER_PÅ_ANDRE_ARBEIDSGIVERE"
                    )
                ) }
            validate { it.requireKey("hendelser", "@id") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        loggUkjentMelding("vedtaksperiode_venter", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
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