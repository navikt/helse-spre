package no.nav.helse.spre.arbeidsgiver

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal val objectMapper: ObjectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())

internal val log: Logger = LoggerFactory.getLogger("sprearbeidsgiver")

fun main() {
    val rapidsConnection = launchApplication(System.getenv())
    rapidsConnection.start()
}

fun launchApplication(
    environment: Map<String, String>
): RapidsConnection {
    val serviceUser = readServiceUserCredentials()
    val arbeidsgiverProducer =
        KafkaProducer<String, InntektsmeldingDTO>(loadBaseConfig(environment.getValue("KAFKA_BOOTSTRAP_SERVERS"), serviceUser).toProducerConfig())

    return RapidApplication.create(environment).apply {
        BeOmInntektsmeldinger(this, arbeidsgiverProducer)
        TrengerIkkeInntektsmelding(this, arbeidsgiverProducer)
    }
}
