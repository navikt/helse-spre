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
import org.apache.kafka.common.errors.*
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*


internal val log = LoggerFactory.getLogger("spre-subsumsjoner")
internal val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val env = System.getenv()
    val kafkaProducer = createProducer(env)
    val config = Config.fromEnv()
    val mappingDao =
        MappingDao(DataSourceBuilder(config.jdbcUrl, config.username, config.password).datasource())
    val rapid = RapidApplication.create(env)
    val publisher = { key: String, value: String ->
        kafkaProducer.send(ProducerRecord(config.subsumsjonTopic, key, value)) { _, err ->
            if (err == null || !isFatalError(err)) return@send
            log.error("Shutting down due to fatal error in subsumsjon-producer: ${err.message}", err)
            rapid.stop()
        }
    }

    // Migrer databasen før vi starter å konsumere fra rapid
    rapid.register(object : RapidsConnection.StatusListener {
        override fun onStartup(rapidsConnection: RapidsConnection) {
            DataSourceBuilder(config.jdbcUrl, config.username, config.password).migratedDataSource()
        }
    })

    rapid.apply {
        SubsumsjonRiver(this, mappingDao) { key, value -> publisher(key, value) }
        SykemeldingRiver(this, mappingDao, IdValidation(config.poisonPills))
        SøknadRiver(this, mappingDao)
        InntektsmeldingRiver(this, mappingDao)
        VedtakFattetRiver(this) { key, value -> publisher(key, value) }
        VedtakForkastetRiver(this) { key, value -> publisher(key, value) }
    }.start()
}

internal class IdValidation(private val poisonPills: List<String>) {
    fun isPoisonous(targetId: String): Boolean = targetId in poisonPills
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
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.LINGER_MS_CONFIG, "0")
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
    }
    return KafkaProducer(properties, StringSerializer(), StringSerializer())
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

