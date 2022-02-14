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
    val kafkaProducer = createProducer(env)
    val config = Config.fromEnv()
    val mappingDao = MappingDao(DataSourceBuilder(config.jdbcUrl, config.username, config.password).getMigratedDataSource())

    RapidApplication.create(env).apply {
        SubsumsjonRiver(this) { key, value -> kafkaProducer.send(ProducerRecord(config.subsumsjonTopic, key, value)) }
        SykemeldingRiver(this, mappingDao)
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
            validate { it.requireKey("subsumsjon") }
            validate { it.requireKey("subsumsjon.versjon") }
            validate { it.requireKey("subsumsjon.kilde") }
            validate { it.requireKey("subsumsjon.versjonAvKode") }
            validate { it.requireKey("subsumsjon.fodselsnummer") }
            validate { it.requireKey("subsumsjon.sporing") }
            validate { it.requireKey("subsumsjon.lovverk") }
            validate { it.requireKey("subsumsjon.lovverksversjon") }
            validate { it.requireKey("subsumsjon.paragraf") }
            validate { it.requireKey("subsumsjon.input") }
            validate { it.requireKey("subsumsjon.output") }
            validate { it.requireKey("subsumsjon.utfall") }
            validate { it.interestedIn("subsumsjon.ledd") }
            validate { it.interestedIn("subsumsjon.punktum") }
            validate { it.interestedIn("subsumsjon.bokstav") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        throw IllegalArgumentException("Feil funnet i subsumsjon melding: $problems")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("mottatt subsumsjon med id: ${packet["@id"]}")
        subsumsjonPublisher(fødselsnummer(packet), subsumsjonMelding(packet))
    }

    private fun fødselsnummer(packet: JsonMessage): String {
        return packet["subsumsjon.fodselsnummer"].asText()
    }

    private fun subsumsjonMelding(packet: JsonMessage) = objectMapper.writeValueAsString(
        mutableMapOf<String, Any?>(
            "id" to packet["@id"],
            "eventName" to "subsumsjon",
            "tidsstempel" to packet["@opprettet"],
            "versjon" to packet["subsumsjon.versjon"],
            "kilde" to packet["subsumsjon.kilde"],
            "versjonAvKode" to packet["subsumsjon.versjonAvKode"],
            "fodselsnummer" to packet["subsumsjon.fodselsnummer"],
            "sporing" to packet["subsumsjon.sporing"],
            "lovverk" to packet["subsumsjon.lovverk"],
            "lovverksversjon" to packet["subsumsjon.lovverksversjon"],
            "paragraf" to packet["subsumsjon.paragraf"],
            "input" to packet["subsumsjon.input"],
            "output" to packet["subsumsjon.output"],
            "utfall" to packet["subsumsjon.utfall"]
        ).apply {
            put("ledd", packet["subsumsjon.ledd"].takeUnless { it.isMissingOrNull() }?.asInt())
            put("punktum", packet["subsumsjon.punktum"].takeUnless { it.isMissingOrNull() }?.asInt())
            put("bokstav", packet["subsumsjon.bokstav"].takeUnless { it.isMissingOrNull() }?.asText())
        }
    )
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

