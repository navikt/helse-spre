package no.nav.helse.spre.oppgaver

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.MessageProblems
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
internal val sikkerLog = LoggerFactory.getLogger("tjenestekall")

internal fun loggUkjentMelding(forventetEventnavn: String, problems: MessageProblems) {
    sikkerLog.error("Forstod ikke $forventetEventnavn: ${problems.toExtendedReport()}")
    log.error("Forstod ikke $forventetEventnavn: $problems")
}

fun interface Publisist {
    fun publiser(dokumentId: String, oppgaveDTO: OppgaveDTO)
}

fun main() {
    launchApplication().start()
}

fun launchApplication(
    environment: Map<String, String> = System.getenv()
): RapidsConnection {
    val kafkaProducer = createProducer(environment)

    val publisist = Publisist { dokumentId: String, oppgave: OppgaveDTO ->
        kafkaProducer.send(ProducerRecord("tbd.spre-oppgaver", dokumentId, oppgave))
    }

    return RapidApplication.create(environment).apply {
        val dsbuilder = DataSourceBuilder()
        val oppgaveDAO = OppgaveDAO(dsbuilder.datasource())

        register(object : RapidsConnection.StatusListener {
            override fun onStartup(rapidsConnection: RapidsConnection) {
                dsbuilder.migrate()
            }
        })
        registerRivers(oppgaveDAO, publisist)
    }
}

internal fun RapidsConnection.registerRivers(
    oppgaveDAO: OppgaveDAO,
    publisist: Publisist
) {
    AvsluttetMedVedtakRiver(this, oppgaveDAO, publisist)
    AvsluttetUtenVedtakRiver(this, oppgaveDAO, publisist)
    SøknadRiver(this, oppgaveDAO, publisist)
    InntektsmeldingRiver(this, oppgaveDAO, publisist)
    VedtaksperiodeEndretRiver(this, oppgaveDAO, publisist)
    VedtaksperiodeVenterRiver(this, oppgaveDAO, publisist)
    VedtaksperiodeForkastetRiver(this, oppgaveDAO, publisist)
    InntektsmeldingerFørSøknadRiver(this, oppgaveDAO, publisist)
    InntektsmeldingHåndtertRiver(this, oppgaveDAO, publisist)
    InntektsmeldingIkkeHåndtertRiver(this, oppgaveDAO, publisist)
    SøknadHåndtertRiver(this, oppgaveDAO, publisist)
    if (System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp") SlettPersonRiver(this, oppgaveDAO)
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

