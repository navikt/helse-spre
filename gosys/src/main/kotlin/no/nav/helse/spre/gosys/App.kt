package no.nav.helse.spre.gosys

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.createAzureTokenClientFromEnvironment
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.retry.retry
import com.github.navikt.tbd_libs.speed.SpeedClient
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import java.time.Duration
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.spre.gosys.annullering.AnnulleringDao
import no.nav.helse.spre.gosys.annullering.AnnulleringRiver
import no.nav.helse.spre.gosys.feriepenger.FeriepengerMediator
import no.nav.helse.spre.gosys.feriepenger.FeriepengerRiver
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtbetaltRiver
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtenUtbetalingRiver
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetRiver
import no.nav.helse.spre.gosys.vedtaksperiodeForkastet.VedtaksperiodeForkastetRiver
import org.flywaydb.core.Flyway
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal val objectMapper: ObjectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .registerModule(JavaTimeModule())

internal val logg: Logger = LoggerFactory.getLogger("spregosys")
internal val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

val erUtvikling = System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp"

fun main() {
    val rapidsConnection = launchApplication(System.getenv())
    rapidsConnection.start()
}

fun launchApplication(
    environment: Map<String, String>
): RapidsConnection {
    val azureClient = createAzureTokenClientFromEnvironment(environment)
    val httpClient = HttpClient {

        install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter(objectMapper))
        }

        install(HttpTimeout) { requestTimeoutMillis = 10000 }
    }
    val joarkClient = JoarkClient(environment.getValue("JOARK_BASE_URL"), azureClient, environment.getValue("JOARK_SCOPE"), httpClient)
    val pdfClient = PdfClient(httpClient, "http://spre-gosys-pdf")
    val eregClient = EregClient(environment.getValue("EREG_BASE_URL"), httpClient)
    val speedClient = SpeedClient(
        httpClient = java.net.http.HttpClient.newHttpClient(),
        objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule()),
        tokenProvider = azureClient
    )

    val hikariConfig = HikariConfig().apply {
        jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", environment.getValue("DB_HOST"), environment.getValue("DB_PORT"), environment.getValue("DB_DATABASE"))
        username = environment.getValue("DB_USERNAME")
        password = environment.getValue("DB_PASSWORD")
        maximumPoolSize = 3
        initializationFailTimeout = Duration.ofMinutes(30).toMillis()
    }

    val dataSource = HikariDataSource(hikariConfig)
    Flyway.configure()
        .dataSource(dataSource)
        .lockRetryCount(-1)
        .load()
        .migrate()

    val duplikatsjekkDao = DuplikatsjekkDao(dataSource)

    val feriepengerMediator = FeriepengerMediator(pdfClient, joarkClient)

    val vedtakFattetDao = VedtakFattetDao(dataSource)
    val utbetalingDao = UtbetalingDao(dataSource)
    val annulleringDao = AnnulleringDao(dataSource)

    return RapidApplication.create(environment)
        .apply {
            settOppRivers(
                duplikatsjekkDao = duplikatsjekkDao,
                feriepengerMediator = feriepengerMediator,
                vedtakFattetDao = vedtakFattetDao,
                utbetalingDao = utbetalingDao,
                annulleringDao = annulleringDao,
                pdfClient = pdfClient,
                joarkClient = joarkClient,
                eregClient = eregClient,
                speedClient = speedClient
            )
        }
}

internal fun RapidsConnection.settOppRivers(
    duplikatsjekkDao: DuplikatsjekkDao,
    feriepengerMediator: FeriepengerMediator,
    vedtakFattetDao: VedtakFattetDao,
    utbetalingDao: UtbetalingDao,
    annulleringDao: AnnulleringDao,
    pdfClient: PdfClient,
    joarkClient: JoarkClient,
    eregClient: EregClient,
    speedClient: SpeedClient
) {
    AnnulleringRiver(this, annulleringDao, duplikatsjekkDao, pdfClient, eregClient, joarkClient, speedClient)
    FeriepengerRiver(this, duplikatsjekkDao, feriepengerMediator)
    VedtakFattetRiver(this, vedtakFattetDao, utbetalingDao, duplikatsjekkDao, pdfClient, joarkClient, eregClient, speedClient)
    UtbetalingUtbetaltRiver(this, utbetalingDao, duplikatsjekkDao)
    UtbetalingUtenUtbetalingRiver(this, utbetalingDao, duplikatsjekkDao)
    VedtaksperiodeForkastetRiver(this, utbetalingDao, annulleringDao, pdfClient, joarkClient, eregClient, speedClient)
}


internal suspend fun<T> HttpStatement.executeRetry(avbryt: (throwable: Throwable) -> Boolean = { false }, block: suspend (response: HttpResponse) -> T) =
    retry(avbryt = avbryt) { execute { block(it) } }
