package no.nav.helse.spre.gosys

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.http.*
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
    val azureClient = AzureClient(
        tokenEndpoint = requireNotNull(environment["AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"]),
        clientId = requireNotNull(environment["AZURE_APP_CLIENT_ID"]),
        clientSecret = requireNotNull(environment["AZURE_APP_CLIENT_SECRET"])
    )
    val httpClient = HttpClient {
        install(JsonFeature) { serializer = JacksonSerializer(objectMapper) }
        install(HttpTimeout) { requestTimeoutMillis = 10000 }
    }
    val joarkClient = JoarkClient(requireNotNull(environment["JOARK_BASE_URL"]), stsRestClient, httpClient)
    val pdfClient = PdfClient(httpClient)

    val eregClient = EregClient(requireNotNull(environment["EREG_BASE_URL"]), stsRestClient, httpClient)
    val pdlClient = PdlClient(azureClient, httpClient, requireNotNull(environment["PDL_CLIENT_SCOPE"]))

    val dataSourceBuilder = DataSourceBuilder(readDatabaseEnvironment())
    dataSourceBuilder.migrate()

    val dataSource = dataSourceBuilder.getDataSource()
    val duplikatsjekkDao = DuplikatsjekkDao(dataSource)

    val vedtakMediator = VedtakMediator(pdfClient, joarkClient)
    val annulleringMediator = AnnulleringMediator(pdfClient, eregClient, joarkClient, pdlClient)
    val feriepengerMediator = FeriepengerMediator(pdfClient, joarkClient)

    val vedtakFattetDao = VedtakFattetDao(dataSource)
    val utbetalingDao = UtbetalingDao(dataSource)

    return RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(environment)).build()
        .apply {
            AnnulleringRiver(this, duplikatsjekkDao, annulleringMediator)
            FeriepengerRiver(this, duplikatsjekkDao, feriepengerMediator)
            VedtakFattetRiver(this, vedtakFattetDao, utbetalingDao, duplikatsjekkDao, vedtakMediator)
            UtbetalingUtbetaltRiver(this, utbetalingDao, vedtakFattetDao, duplikatsjekkDao, vedtakMediator)
            UtbetalingUtenUtbetalingRiver(this, utbetalingDao, vedtakFattetDao, duplikatsjekkDao, vedtakMediator)
        }
}
