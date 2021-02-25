package no.nav.helse.spre.arbeidsgiver

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader

internal class TrengerIkkeInntektsmelding(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverProducer: KafkaProducer<String, InntektsmeldingDTO>
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "trenger_ikke_inntektsmelding")
                it.requireKey("organisasjonsnummer", "fødselsnummer", "vedtaksperiodeId")
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)

    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Trenger ikke inntektsmelding for vedtaksperiode: {}", packet["vedtaksperiodeId"].asText())

        val payload = packet.tilInntektsmeldingDTO()
        arbeidsgiverProducer.send(ProducerRecord(
            "aapen-helse-spre-arbeidsgiver",
            null,
            payload.fødselsnummer,
            payload,
            listOf(RecordHeader("type", payload.meldingstype))
        )).get()
    }

    private fun JsonMessage.tilInntektsmeldingDTO() = InntektsmeldingDTO.trengerIkkeInntektsmelding(
        organisasjonsnummer = this["organisasjonsnummer"].asText(),
        fødselsnummer = this["fødselsnummer"].asText(),
        fom = this["fom"].asLocalDate(),
        tom = this["tom"].asLocalDate(),
        opprettet = this["@opprettet"].asLocalDateTime()
    )
}
