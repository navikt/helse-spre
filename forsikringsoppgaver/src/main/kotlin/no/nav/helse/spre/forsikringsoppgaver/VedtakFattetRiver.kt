package no.nav.helse.spre.forsikringsoppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry

private const val EVENT_NAME = "vedtak_fattet"

class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveoppretterClient,
    private val forsikringsgrunnlagClient: ForsikringsgrunnlagClient
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", EVENT_NAME)
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
        teamLogs.info("Mottatt melding $EVENT_NAME. Melding: ${packet.toJson()}")
        val forsikringsgrunnlag = forsikringsgrunnlagClient
            .forsikringsgrunnlag(behandlingId)
            ?: error("Fant ikke forsikringsgrunnlag for $behandlingId")
        val premiegrunnlag = forsikringsgrunnlag.premiegrunnlag.toBigDecimal()

        val duplikatkontrollId = packet["@id"].asUuid()

        if (sykepengegrunnlag != premiegrunnlag) {
            val avviksprosent = beregnAvvik(sykepengegrunnlag, premiegrunnlag)
            teamLogs.info(
                """Avvik mellom sykepengegrunnlag og premiegrunnlag. Oppretter oppgave.
                   Sykepengegrunnlag: ${sykepengegrunnlag.setScale(2)}
                   Premiegrunnlag: ${forsikringsgrunnlag.premiegrunnlag}
                   Avviksprosent: ${avviksprosent.setScale(2)}
                   Forsikringsgrunnlag: ${forsikringsgrunnlag.toJsonString()}
                """.trimIndent()
            )
            oppgaveClient.lagOppgave(
                duplikatkontrollId,
                fødselsnummer,
                Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag(sykepengegrunnlag, premiegrunnlag, avviksprosent),
                skjæringstidspunkt
            )
        } else {
            teamLogs.info(
                """Ikke avvik mellom sykepengegrunnlag og premiegrunnlag. Oppretter ikke oppgave.
                   Sykepengegrunnlag: ${sykepengegrunnlag.setScale(2)}
                   Premiegrunnlag: ${forsikringsgrunnlag.premiegrunnlag}
                   Forsikringsgrunnlag: ${forsikringsgrunnlag.toJsonString()}
                """.trimIndent()
            )
        }
    }
}
