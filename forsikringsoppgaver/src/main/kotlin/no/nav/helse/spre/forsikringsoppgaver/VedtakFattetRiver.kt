package no.nav.helse.spre.forsikringsoppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.math.BigDecimal

class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveoppretterClient,
    private val forsikringsgrunnlagClient: ForsikringsgrunnlagClient
) : River.PacketListener {
    companion object {
        private val AKSEPTABELT_AVVIK = BigDecimal("30")
    }
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
        val sykepengegrunnlag = packet["sykepengegrunnlag"].asText().toBigDecimal()
        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
        val behandlingId = BehandlingId(packet["behandlingId"].asUuid())
        val forsikringsgrunnlag = forsikringsgrunnlagClient
            .forsikringsgrunnlag(behandlingId)
            ?: error("Fant ikke forsikringsgrunnlag for $behandlingId")

        val duplikatkontrollId = packet["@id"].asUuid()

        if (forsikringsgrunnlag.premiegrunnlag == null) return

        val avviksprosent = beregnAvvik(sykepengegrunnlag, forsikringsgrunnlag.premiegrunnlag.toBigDecimal())
        if (avviksprosent > AKSEPTABELT_AVVIK) {
            oppgaveClient.lagOppgave(
                duplikatkontrollId,
                fødselsnummer,
                Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag(sykepengegrunnlag, forsikringsgrunnlag.premiegrunnlag.toBigDecimal(), avviksprosent),
                skjæringstidspunkt
            )
        }
    }

}
