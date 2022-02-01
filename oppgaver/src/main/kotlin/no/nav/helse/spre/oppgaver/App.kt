package no.nav.helse.spre.oppgaver

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.util.*

internal val objectMapper: ObjectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())

internal val log = LoggerFactory.getLogger("helse-spre-oppgaver")

fun interface Publisist {
    fun publiser(oppgaveDTO: OppgaveDTO): Any
}

fun main() {
    launchApplication().start()
}

fun launchApplication(
    environment: Map<String, String> = System.getenv()
): RapidsConnection {
    val datasource = DataSourceBuilder()
        .apply(DataSourceBuilder::migrate)
        .getDataSource()

    val oppgaveDAO = OppgaveDAO(datasource)

    val kafkaProducer = createProducer(environment)

    val publisist = { oppgave: OppgaveDTO -> kafkaProducer.send(ProducerRecord("tbd.spre-oppgaver", oppgave)) }

    return RapidApplication.create(environment).apply {
        registerRivers(oppgaveDAO, publisist)
    }
}

internal fun RapidsConnection.registerRivers(
    oppgaveDAO: OppgaveDAO,
    publisist: Publisist
) {
    RegistrerSøknader(this, oppgaveDAO)
    RegistrerInntektsmeldinger(this, oppgaveDAO)
    HåndterVedtaksperiodeendringer(this, oppgaveDAO, publisist)
    HåndterHendelseIkkeHåndtert(this, oppgaveDAO, publisist)
    HåndterOpprettOppgaveForSpeilsaksbehandlere(this, oppgaveDAO, publisist)
    HåndterOpprettOppgave(this, oppgaveDAO, publisist)
}

private fun createProducer(env: Map<String, String>): KafkaProducer<String, OppgaveDTO> {
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

        put(ProducerConfig.ACKS_CONFIG, "1")
        put(ProducerConfig.LINGER_MS_CONFIG, "0")
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
    }
    return KafkaProducer(properties, StringSerializer(), JacksonSerializer())
}

