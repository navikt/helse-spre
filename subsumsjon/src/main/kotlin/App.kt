package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.*
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.util.*


// TODO: vedtak fattet og vedtak forkastes må inn på subsumsjon topics


internal val log = LoggerFactory.getLogger("helse-spre-subsumsjoner")

fun main() {
    val env = System.getenv()
    val topic = env.get("SUBSUMSJON_TOPIC") ?: throw IllegalArgumentException("SUBSUMSJON_TOPIC is required")
    val kafkaProducer = createProducer(env)

    RapidApplication.create(env).apply {
        SubsumsjonRiver(this) { key, value -> kafkaProducer.send(ProducerRecord(topic, key, value)) }
    }.start()
}

internal class SubsumsjonRiver(
    rapidsConnection: RapidsConnection,
    private val subsumsjonPublisher: (key: String, value: String) -> Unit
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "subsumsjon") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("@opprettet") }
            validate { it.requireKey("versjon") }
            validate { it.requireKey("kilde") }
            validate { it.requireKey("versjonAvKode") }
            validate { it.requireKey("fodselsnummer") }
            validate { it.requireKey("sporing") }
            validate { it.requireKey("lovverk") }
            validate { it.requireKey("lovverkVersjon") }
            validate { it.requireKey("paragraf") }
            validate { it.requireKey("input") }
            validate { it.requireKey("output") }
            validate { it.requireKey("utfall") }
            validate { it.interestedIn("ledd") }
            validate { it.interestedIn("punktum") }
            validate { it.interestedIn("bokstav") }

        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        //throw IllegalArgumentException("Feil funnet i subsumsjon melding: $problems")
        log.warn("Fant subsumsjon melding med feil format: $problems")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("mottatt subsumsjon med id: ${packet["@id"]}")
        subsumsjonPublisher(
            fødselsnummer(packet),
            subsumsjonMelding(packet)
        )
    }

    private fun fødselsnummer(packet: JsonMessage): String {
        return packet["fodselsnummer"].asText()
    }

    private fun subsumsjonMelding(packet: JsonMessage) = objectMapper.writeValueAsString(mutableMapOf(
        "@id" to packet["@id"],
        "@event_name" to "subsumsjon",
        "@opprettet" to packet["@opprettet"],
        "versjon" to packet["versjon"],
        "kilde" to packet["kilde"],
        "versjonAvKode" to packet["versjonAvKode"],
        "fodselsnummer" to packet["fodselsnummer"],
        "sporing" to packet["sporing"],
        "lovverk" to packet["lovverk"],
        "lovverkVersjon" to packet["lovverkVersjon"],
        "paragraf" to packet["paragraf"],
        "input" to packet["input"],
        "output" to packet["output"],
        "utfall" to packet["utfall"]
    ).apply {
        compute("ledd") { _, _ -> packet["ledd"].takeIf { !it.isNull }?.asText() }
        compute("punktum") { _, _ -> packet["punktum"].takeIf { !it.isNull }?.asText() }
        compute("bokstav") { _, _ -> packet["bokstav"].takeIf { !it.isNull }?.asText() }
    })
}

private fun createProducer(env: Map<String, String>): KafkaProducer<String, String> {
    val properties = Properties().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, env.getValue("KAFKA_BROKERS"))
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name)
        put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
        put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
        put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, env.getValue("KAFKA_TRUSTSTORE_PATH"))
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, env.getValue("KAFKA_CREDSTORE_PASSWORD"))
        put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, env.getValue("KAFKA_KEYSTORE_PATH"))
        put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, env.getValue("KAFKA_CREDSTORE_PASSWORD"))

        put(ProducerConfig.CLIENT_ID_CONFIG, env.getValue("KAFKA_CONSUMER_GROUP_ID"))
        put(ProducerConfig.ACKS_CONFIG, "1")
        put(ProducerConfig.LINGER_MS_CONFIG, "0")
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
    }
    return KafkaProducer(properties, StringSerializer(), StringSerializer())
}

internal val objectMapper: ObjectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())

