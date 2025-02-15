package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.createAzureTokenClientFromEnvironment
import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import com.github.navikt.tbd_libs.spedisjon.SpedisjonClient
import no.nav.helse.rapids_rivers.RapidApplication
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.http.HttpClient

internal val log = LoggerFactory.getLogger("spre-subsumsjoner")
internal val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val env = System.getenv()
    val factory = ConsumerProducerFactory(AivenConfig.default)
    val kafkaProducer = factory.createProducer()
    val rapid = RapidApplication.create(env, factory)
    val subsumsjonTopic = requireNotNull(env["SUBSUMSJON_TOPIC"]) { " SUBSUMSJON_TOPIC is required config " }
    val publisher = { key: String, value: String ->
        kafkaProducer.send(ProducerRecord(subsumsjonTopic, key, value)) { _, err ->
            if (err == null || !isFatalError(err)) return@send
            log.error("Shutting down due to fatal error in subsumsjon-producer: ${err.message}", err)
            rapid.stop()
        }
    }

    val azureClient = createAzureTokenClientFromEnvironment(env)
    val spedisjonClient = SpedisjonClient(
        httpClient = HttpClient.newHttpClient(),
        objectMapper = objectMapper,
        tokenProvider = azureClient
    )

    rapid.apply {
        SubsumsjonV1_0_0River(this, spedisjonClient) { key, value -> publisher(key, value) }
        SubsumsjonUkjentVersjonRiver(this)
        VedtakFattetRiver(this) { key, value -> publisher(key, value) }
        VedtakForkastetRiver(this) { key, value -> publisher(key, value) }
    }.start()
}

private fun isFatalError(err: Exception) = when (err) {
    is InvalidTopicException,
    is RecordBatchTooLargeException,
    is RecordTooLargeException,
    is UnknownServerException,
    is AuthorizationException -> true
    else -> false
}

internal val objectMapper: ObjectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())

