package no.nav.helse.spre.subsumsjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.Logger
import org.slf4j.LoggerFactory


private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

class VedtakForkastetRiver (
    rapidsConnection: RapidsConnection,
    private val subsumsjonPublisher: (key: String, value: String) -> Unit
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { message ->
                message.demandValue("@event_name", "vedtaksperiode_forkastet")
                message.requireKey("@opprettet", "aktørId", "vedtaksperiodeId")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        subsumsjonPublisher(fødselsnummer(packet), vedtak_forkastet(packet))
    }

    private fun fødselsnummer(packet: JsonMessage): String {
        return packet["fødselsnummer"].asText()
    }

    private fun vedtak_forkastet(packet: JsonMessage): String {
        return objectMapper.writeValueAsString(
            mutableMapOf<String, Any?>(
                "id" to packet["@id"],
                "eventName" to "vedtaksperiodeForkastet",
                "tidsstempel" to packet["@opprettet"],
                "fodselsnummer" to packet["fødselsnummer"],
                "vedtaksperiodeId" to packet["vedtaksperiodeId"],
                "organisasjonsnummer" to packet["organisasjonsnummer"],

                )).also { sikkerLogg.info("sender vedtaksperiode_forkastet: $it") }
    }
}
