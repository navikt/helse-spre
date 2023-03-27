package no.nav.helse.spre.arbeidsgiver

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import no.nav.helse.spre.Toggle
import no.nav.helse.spre.arbeidsgiver.InntektsmeldingDTO.Companion.tilInntektsmeldingDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.LoggerFactory
import java.util.*

internal class BeOmInntektsmeldinger(
    private val rapidsConnection: RapidsConnection,
    private val arbeidsgiverProducer: KafkaProducer<String, InntektsmeldingDTO>
) : River.PacketListener{
    private companion object {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "trenger_inntektsmelding")
                it.requireKey("organisasjonsnummer", "fødselsnummer", "vedtaksperiodeId")
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("forstår ikke trenger_inntektsmelding:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Ber om inntektsmelding på vedtaksperiode: {}", packet["vedtaksperiodeId"].asText())

        val payload = packet.tilInntektsmeldingDTO(meldingstype = Meldingstype.TRENGER_INNTEKTSMELDING)
        val topicName = "tbd.aapen-helse-spre-arbeidsgiver"
        log.info("Publiserer behov for inntektsmelding på vedtak: ${packet["vedtaksperiodeId"].textValue()}:\n{}", objectMapper.writeValueAsString(payload))
        arbeidsgiverProducer.send(ProducerRecord(
            topicName,
            null,
            payload.fødselsnummer,
            payload,
            listOf(RecordHeader("type", payload.meldingstype))
        )).get()
        log.info("Publiserte behov for inntektsmelding på vedtak: ${packet["vedtaksperiodeId"].textValue()}")

        rapidsConnection.publish(JsonMessage.newMessage(
            mapOf(
                "@event_name" to "publisert_behov_for_inntektsmelding",
                "@id" to UUID.randomUUID(),
                "vedtaksperiodeId" to packet["vedtaksperiodeId"]
            )
        ).toJson())
    }

}
