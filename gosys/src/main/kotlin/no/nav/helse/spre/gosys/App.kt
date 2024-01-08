package no.nav.helse.spre.gosys

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.gosys.annullering.AnnulleringMediator
import no.nav.helse.spre.gosys.annullering.AnnulleringRiver
import no.nav.helse.spre.gosys.feriepenger.FeriepengerMediator
import no.nav.helse.spre.gosys.feriepenger.FeriepengerRiver
import no.nav.helse.spre.gosys.pdl.PdlClient
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtbetaltRiver
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtenUtbetalingRiver
import no.nav.helse.spre.gosys.vedtak.VedtakMediator
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetRiver
import org.flywaydb.core.Flyway
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

internal val objectMapper: ObjectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .registerModule(JavaTimeModule())

internal val log: Logger = LoggerFactory.getLogger("spregosys")
internal val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

val erUtvikling = System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp"

fun main() {
    val rapidsConnection = launchApplication(System.getenv())
    rapidsConnection.start()
}

fun launchApplication(
    environment: Map<String, String>
): RapidsConnection {
    val azureClient = AzureClient(
        tokenEndpoint = environment.getValue("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
        clientId = environment.getValue("AZURE_APP_CLIENT_ID"),
        clientSecret = environment.getValue("AZURE_APP_CLIENT_SECRET")
    )
    val httpClient = HttpClient {

        install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter(objectMapper))
        }

        install(HttpTimeout) { requestTimeoutMillis = 10000 }
    }
    val joarkClient = JoarkClient(environment.getValue("JOARK_BASE_URL"), azureClient, environment.getValue("JOARK_SCOPE"), httpClient)
    val pdfClient = PdfClient(httpClient, "http://spre-gosys-pdf")
    val eregClient = EregClient(environment.getValue("EREG_BASE_URL"), httpClient)
    val pdlClient = PdlClient(azureClient, httpClient, environment.getValue("PDL_BASE_URL"), environment.getValue("PDL_CLIENT_SCOPE"))

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

    val vedtakMediator = VedtakMediator(pdfClient, joarkClient, eregClient, pdlClient)
    val annulleringMediator = AnnulleringMediator(pdfClient, eregClient, joarkClient, pdlClient)
    val feriepengerMediator = FeriepengerMediator(pdfClient, joarkClient)

    val vedtakFattetDao = VedtakFattetDao(dataSource)
    val utbetalingDao = UtbetalingDao(dataSource)

    return RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(environment)).build()
        .apply {
            settOppRivers(duplikatsjekkDao, annulleringMediator, feriepengerMediator, vedtakFattetDao, utbetalingDao, vedtakMediator)
        }
}

internal fun RapidsConnection.settOppRivers(
    duplikatsjekkDao: DuplikatsjekkDao,
    annulleringMediator: AnnulleringMediator,
    feriepengerMediator: FeriepengerMediator,
    vedtakFattetDao: VedtakFattetDao,
    utbetalingDao: UtbetalingDao,
    vedtakMediator: VedtakMediator,
) {
    AnnulleringRiver(this, duplikatsjekkDao, annulleringMediator)
    FeriepengerRiver(this, duplikatsjekkDao, feriepengerMediator)
    VedtakFattetRiver(this, vedtakFattetDao, utbetalingDao, duplikatsjekkDao, vedtakMediator)
    UtbetalingUtbetaltRiver(this, utbetalingDao, vedtakFattetDao, duplikatsjekkDao, vedtakMediator)
    UtbetalingUtenUtbetalingRiver(this, utbetalingDao, vedtakFattetDao, duplikatsjekkDao, vedtakMediator)
}
