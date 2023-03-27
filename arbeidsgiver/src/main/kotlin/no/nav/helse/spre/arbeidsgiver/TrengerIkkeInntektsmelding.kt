package no.nav.helse.spre.arbeidsgiver

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.rapids_rivers.*
import no.nav.helse.spre.Toggle
import no.nav.helse.spre.arbeidsgiver.InntektsmeldingDTO.Companion.tilInntektsmeldingDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.LoggerFactory

internal class TrengerIkkeInntektsmelding(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverProducer: KafkaProducer<String, InntektsmeldingDTO>
) : River.PacketListener {
    private companion object {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "trenger_ikke_inntektsmelding")
                it.requireKey("organisasjonsnummer", "fødselsnummer", "vedtaksperiodeId")
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("forstår ikke trenger_ikke_inntektsmelding:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Trenger ikke inntektsmelding for vedtaksperiode: {}", packet["vedtaksperiodeId"].asText())

        val payload = packet.tilInntektsmeldingDTO(Meldingstype.TRENGER_IKKE_INNTEKTSMELDING)
        val topicName = "tbd.aapen-helse-spre-arbeidsgiver"
        sikkerLogg.info("Publiserer ikke inntektsmelding for vedtaksperiode {}:\n{}", packet["vedtaksperiodeId"].asText(), objectMapper.writeValueAsString(payload))

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
