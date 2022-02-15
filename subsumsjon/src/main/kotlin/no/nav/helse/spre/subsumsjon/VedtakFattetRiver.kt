package no.nav.helse.spre.subsumsjon

import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val subsumsjonPublisher: (key: String, value: String) -> Unit
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { message ->
                message.demandValue("@event_name", "vedtak_fattet")
                message.requireKey(
                    "@id",
                    "hendelser",
                    "fødselsnummer",
                    "vedtaksperiodeId",
                    "skjæringstidspunkt",
                    "fom",
                    "tom",
                    "utbetalingId",
                    "organisasjonsnummer",
                    "@opprettet"
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        subsumsjonPublisher(fødselsnummer(packet), vedtak_fattet(packet))
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
                "utbetalingId" to packet["utbetalingId"],
                "organisasjonsnummer" to packet["organisasjonsnummer"],

            )).also { sikkerLogg.info("sender vedtak_fattet: $it") }
    }
}