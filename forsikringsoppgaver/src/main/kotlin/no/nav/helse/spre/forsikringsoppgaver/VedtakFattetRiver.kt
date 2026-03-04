package no.nav.helse.spre.forsikringsoppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry

class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveoppretterClient,
    private val forsikringsgrunnlagClient: ForsikringsgrunnlagClient
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "vedtak_fattet")
                it.requireValue("yrkesaktivitetstype", "SELVSTENDIG")
                it.requireContains("tags", "Førstegangsbehandling")
            }
            validate {
                it.requireKey("fødselsnummer", "behandlingId", "sykepengegrunnlag", "skjæringstidspunkt", "@id")
            }
        }.register(this)
    }
    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val sykepengegrunnlag = packet["sykepengegrunnlag"].asBigDecimal()
        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
        val behandlingId = BehandlingId(packet["behandlingId"].asUuid())
        val forsikringsgrunnlag = forsikringsgrunnlagClient
            .forsikringsgrunnlag(behandlingId)
            ?: error("Fant ikke forsikringsgrunnlag for $behandlingId")

        val duplikatkontrollId = packet["@id"].asUuid()

        if (forsikringsgrunnlag.premiegrunnlag == null) return

        if (forStortAvvik(sykepengegrunnlag, forsikringsgrunnlag.premiegrunnlag)) {
            oppgaveClient.lagOppgave(
                duplikatkontrollId,
                fødselsnummer,
                Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag
            )
        }
    }

}
