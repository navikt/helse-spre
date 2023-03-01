package no.nav.helse.spre.arbeidsgiver

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import no.nav.helse.spre.Toggle
import no.nav.helse.spre.arbeidsgiver.InntektsmeldingDTO.Companion.tilInntektsmeldingDTO
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

        val payload = packet.tilInntektsmeldingDTO(Meldingstype.TRENGER_IKKE_INNTEKTSMELDING)
        val topicName =
            if (Toggle.ArbeidsgiverAiventopic.enabled) "tbd.aapen-helse-spre-arbeidsgiver"
            else "aapen-helse-spre-arbeidsgiver"
        arbeidsgiverProducer.send(
            ProducerRecord(
                topicName,
                null,
                payload.fødselsnummer,
                payload,
                listOf(RecordHeader("type", payload.meldingstype))
            )
        ).get()

        log.info("Publiserte trenger ikke inntektsmelding for vedtak: ${packet["vedtaksperiodeId"].textValue()}")
    }
}
