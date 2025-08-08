package no.nav.helse.spre.subsumsjon

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.time.LocalDate
import java.time.LocalDateTime


class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val subsumsjonPublisher: (key: String, value: String) -> Unit
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "vedtak_fattet") }
            validate { message ->
                message.requireKey(
                    "@id",
                    "hendelser",
                    "fødselsnummer",
                    "vedtaksperiodeId",
                    "skjæringstidspunkt",
                    "fom",
                    "tom",
                    "organisasjonsnummer",
                    "@opprettet"
                )
                message.interestedIn("utbetalingId")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val datoForVedtak = LocalDateTime.parse(packet["@opprettet"].asText()).toLocalDate()
        val startdatoSubsumsjoner = LocalDate.of(2022, 2, 15)
        if (datoForVedtak < startdatoSubsumsjoner) return
        subsumsjonPublisher(fødselsnummer(packet), vedtak_fattet(packet))
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        sikkerLogg.error("Feil under validering av vedtak_fattet problems: ${problems.toExtendedReport()} ")
        throw IllegalArgumentException("Feil under validering av vedtak_fattet")
    }

    private fun fødselsnummer(packet: JsonMessage): String {
        return packet["fødselsnummer"].asText()
    }

    private fun vedtak_fattet(packet: JsonMessage): String {
        return objectMapper.writeValueAsString(
            mutableMapOf<String, Any?>(
                "id" to packet["@id"],
                "eventName" to "vedtakFattet",
                "tidsstempel" to packet["@opprettet"],
                "hendelser" to packet["hendelser"],
                "fodselsnummer" to packet["fødselsnummer"],
                "vedtaksperiodeId" to packet["vedtaksperiodeId"],
                "skjeringstidspunkt" to packet["skjæringstidspunkt"],
                "fom" to packet["fom"],
                "tom" to packet["tom"],
                "organisasjonsnummer" to packet["organisasjonsnummer"]
            ).apply {
                put("utbetalingId", packet["utbetalingId"].takeUnless { it.isMissingOrNull() }?.asText())
            }
        ).also { sikkerLogg.info("sender vedtak_fattet: $it") }
    }
}