package no.nav.helse.spre.gosys

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.gosys.annullering.AnnulleringMediator
import no.nav.helse.spre.gosys.annullering.AnnulleringRiver
import no.nav.helse.spre.gosys.feriepenger.FeriepengerMediator
import no.nav.helse.spre.gosys.feriepenger.FeriepengerRiver
import no.nav.helse.spre.gosys.io.IO
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtbetaltRiver
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtenUtbetalingRiver
import no.nav.helse.spre.gosys.vedtak.VedtakConsumer
import no.nav.helse.spre.gosys.vedtak.VedtakMediator
import no.nav.helse.spre.gosys.vedtak.VedtakMessage
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetRiver
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

internal val objectMapper: ObjectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())

internal val log: Logger = LoggerFactory.getLogger("spregosys")
internal val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val rapidsConnection = launchApplication(System.getenv())
    rapidsConnection.start()
}

fun launchApplication(
    environment: Map<String, String>
): RapidsConnection {
    val serviceUser = readServiceUserCredentials()
    val stsRestClient = StsRestClient(requireNotNull(environment["STS_URL"]), serviceUser)
    val httpClient = HttpClient {
        install(JsonFeature) { serializer = JacksonSerializer(objectMapper) }
        install(HttpTimeout) { requestTimeoutMillis = 10000 }
    }
    val joarkClient = JoarkClient(requireNotNull(environment["JOARK_BASE_URL"]), stsRestClient, httpClient)
    val pdfClient = PdfClient(httpClient)

    val dataSourceBuilder = DataSourceBuilder(readDatabaseEnvironment())
    dataSourceBuilder.migrate()

    val dataSource = dataSourceBuilder.getDataSource()
    val duplikatsjekkDao = DuplikatsjekkDao(dataSource)
    val vedtakMediator = VedtakMediator(pdfClient, joarkClient, duplikatsjekkDao)
    GlobalScope.launch(Dispatchers.IO) {
        startRyddejobbConsumer(environment)
    }
    val annulleringMediator = AnnulleringMediator(pdfClient, joarkClient, duplikatsjekkDao)
    val feriepengerMediator = FeriepengerMediator(pdfClient, joarkClient, duplikatsjekkDao)

    val vedtakFattetDao = VedtakFattetDao(dataSource)
    val utbetalingDao = UtbetalingDao(dataSource)

    return RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(environment)).build()
        .apply {
            AnnulleringRiver(this, annulleringMediator)
            FeriepengerRiver(this, feriepengerMediator)
            VedtakFattetRiver(this, vedtakFattetDao, utbetalingDao, vedtakMediator)
            UtbetalingUtbetaltRiver(this, utbetalingDao, vedtakFattetDao, vedtakMediator)
            UtbetalingUtenUtbetalingRiver(this, utbetalingDao, vedtakFattetDao, vedtakMediator)
        }
}

fun startRyddejobbConsumer(env: Map<String, String>) {
    val kafkaConfig = Properties().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, env.getValue("KAFKA_BROKERS"))
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
        put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, env.getValue("KAFKA_TRUSTSTORE_PATH"))
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, env.getValue("KAFKA_CREDSTORE_PASSWORD"))
        put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, env.getValue("KAFKA_KEYSTORE_PATH"))
        put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, env.getValue("KAFKA_CREDSTORE_PASSWORD"))
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(ConsumerConfig.GROUP_ID_CONFIG, "spre-gosys-laste-v1")
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    }
    VedtakConsumer(KafkaConsumer(kafkaConfig, StringDeserializer(), StringDeserializer())).consume()
}

fun Application.wiring(
    environment: Map<String, String>,
    vedtakMediator: VedtakMediator
) {
    install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
    basicAuthentication(environment.getValue("ADMIN_SECRET"))
    routing {
        authenticate("admin") {
            adminGrensesnitt(vedtakMediator)
        }
    }
}

internal fun Route.adminGrensesnitt(
    vedtakMediator: VedtakMediator
) {
    route("/admin") {
        post("/utbetaling") {
            log.info("Leser inn utbetalinger")
            val utbetaling = call.receive<ArrayNode>()
            utbetaling.forEachIndexed { index, json ->
                try {
                    val format = Json { ignoreUnknownKeys = true }
                    val vedtak: IO.Vedtak = format.decodeFromString(json.toString())
                    val vedtakMessage = VedtakMessage(vedtak)
                    log.info("Behandler utbetaling {}", vedtakMessage.hendelseId)
                    sikkerLogg.info(json.toString())
                    vedtakMediator.opprettVedtak(vedtakMessage)
                } catch (error: RuntimeException) {
                    log.error("Kritisk feil, se sikker logg for fullstendig feilmelding")
                    sikkerLogg.error("Kritisk feil for index $index", error, json.toString())
                }
            }
            call.respond(HttpStatusCode.OK)
        }
    }
}
