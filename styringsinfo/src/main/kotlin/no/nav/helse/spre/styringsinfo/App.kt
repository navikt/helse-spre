package no.nav.helse.spre.styringsinfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.navikt.tbd_libs.azure.createAzureTokenClientFromEnvironment
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.speed.SpeedClient
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.spre.styringsinfo.teamsak.behandling.PostgresBehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.enhet.NavOrganisasjonsmasterClient
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.http.HttpClient

internal val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

fun main() {
    val environment = System.getenv()
    val dataSourceBuilder = DataSourceBuilder(environment)
    val dataSource = dataSourceBuilder.getDataSource()

    val azureClient = createAzureTokenClientFromEnvironment()

    val nomClient = NavOrganisasjonsmasterClient(
        baseUrl = environment.getValue("NOM_API_BASE_URL"),
        scope = environment.getValue("NOM_API_OAUTH_SCOPE"),
        azureClient = azureClient
    )

    val speedClient = SpeedClient(
        httpClient = HttpClient.newHttpClient(),
        objectMapper = objectMapper,
        tokenProvider = azureClient
    )
    val rapidsConnection = launchApplication(dataSource, environment, nomClient, speedClient)
    rapidsConnection.register(object: RapidsConnection.StatusListener {
        override fun onStartup(rapidsConnection: RapidsConnection) {
            dataSourceBuilder.migrate()
        }
    })
    rapidsConnection.start()
}

internal fun launchApplication(dataSource: HikariDataSource, environment: Map<String, String>, nom: NavOrganisasjonsmasterClient, speedClient: SpeedClient): RapidsConnection {
    val hendelseDao = PostgresHendelseDao(dataSource)
    val behandlingshendelseDao = PostgresBehandlingshendelseDao(dataSource)

    return RapidApplication.create(environment).apply {
        BehandlingOpprettet.river(this, hendelseDao, behandlingshendelseDao, speedClient)
        VedtakFattet.river(this, hendelseDao, behandlingshendelseDao)
        AvsluttetUtenVedtak.river(this, hendelseDao, behandlingshendelseDao)
        BehandlingForkastet.river(this, hendelseDao, behandlingshendelseDao)
        VedtaksperiodeAvvist.river(this, hendelseDao, behandlingshendelseDao, nom)
        VedtaksperiodeGodkjent.river(this, hendelseDao, behandlingshendelseDao, nom)
        VedtaksperiodeAnnullert.river(this, hendelseDao, behandlingshendelseDao)
        VedtaksperioderVenterIndirektePåGodkjenning.river(this, hendelseDao, behandlingshendelseDao)
        UtkastTilVedtak.river(this, hendelseDao, behandlingshendelseDao)
    }
}
