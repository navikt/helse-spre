package no.nav.helse.spre.styringsinfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.navikt.tbd_libs.azure.createAzureTokenClientFromEnvironment
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.spre.styringsinfo.teamsak.NavOrganisasjonsmasterClient
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.datafortelling.SendtSøknadArbeidsgiverRiver
import no.nav.helse.spre.styringsinfo.datafortelling.SendtSøknadNavRiver
import no.nav.helse.spre.styringsinfo.datafortelling.VedtakFattetRiver
import no.nav.helse.spre.styringsinfo.datafortelling.VedtakForkastetRiver
import no.nav.helse.spre.styringsinfo.datafortelling.db.SendtSøknadDao
import no.nav.helse.spre.styringsinfo.datafortelling.db.SendtSøknadPatcher
import no.nav.helse.spre.styringsinfo.datafortelling.db.VedtakFattetDao
import no.nav.helse.spre.styringsinfo.datafortelling.db.VedtakFattetPatcher
import no.nav.helse.spre.styringsinfo.datafortelling.db.VedtakForkastetDao
import no.nav.helse.spre.styringsinfo.datafortelling.db.VedtakForkastetPatcher
import no.nav.helse.spre.styringsinfo.datafortelling.domain.SendtSøknadPatch
import no.nav.helse.spre.styringsinfo.datafortelling.domain.VedtakFattetPatch
import no.nav.helse.spre.styringsinfo.datafortelling.domain.VedtakForkastetPatch
import no.nav.helse.spre.styringsinfo.teamsak.behandling.PostgresBehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.*
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.PostgresHendelseDao
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.concurrent.thread

internal val log: Logger = LoggerFactory.getLogger("sprestyringsinfo")
internal val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

data class PatchOptions(
    val patchLevelMindreEnn: Int,
    val initialSleepMillis: Long = 60_000, // Antall millisekunder vi venter før patching starter.
    val loopSleepMillis: Long = 1000, // Antall millisekunder mellom hver iterasjon
    val antallMeldinger: Int = 1000 // Hvor mange meldinger vi henter fra databasen for hver iterasjon.
)

fun main() {
    val environment = System.getenv()
    val dataSourceBuilder = DataSourceBuilder(environment)
    val dataSource = dataSourceBuilder.getDataSource()
    val sendtSøknadPatcher = SendtSøknadPatcher(SendtSøknadDao(dataSource))
    val vedtakFattetPatcher = VedtakFattetPatcher(VedtakFattetDao(dataSource))
    val vedtakForkastetPatcher = VedtakForkastetPatcher(VedtakForkastetDao(dataSource))

    val azureClient = createAzureTokenClientFromEnvironment()

    val nomClient = NavOrganisasjonsmasterClient(
        baseUrl = environment.getValue("NOM_API_BASE_URL"),
        scope = environment.getValue("NOM_API_OAUTH_SCOPE"),
        azureClient = azureClient
    )

    val rapidsConnection = launchApplication(dataSource, environment, nomClient)
    rapidsConnection.register(object: RapidsConnection.StatusListener {
        override fun onStartup(rapidsConnection: RapidsConnection) {
            dataSourceBuilder.migrate()
            thread {
                sendtSøknadPatcher.patchSendtSøknad(PatchOptions(patchLevelMindreEnn = SendtSøknadPatch.values().last().ordinal))
            }
            thread {
                vedtakFattetPatcher.patchVedtakFattet(PatchOptions(
                    patchLevelMindreEnn = VedtakFattetPatch.values().last().ordinal,
                    initialSleepMillis = 1000
                ))
            }
            thread {
                vedtakForkastetPatcher.patchVedtakForkastet(PatchOptions(
                    patchLevelMindreEnn = VedtakForkastetPatch.values().last().ordinal,
                    initialSleepMillis = 1000
                ))
            }
        }
    })
    rapidsConnection.start()
}

internal fun launchApplication(dataSource: HikariDataSource, environment: Map<String, String>, nom: NavOrganisasjonsmasterClient): RapidsConnection {
    val sendtSøknadDao = SendtSøknadDao(dataSource)
    val vedtakFattetDao = VedtakFattetDao(dataSource)
    val vedtakForkastetDao = VedtakForkastetDao(dataSource)

    val hendelseDao = PostgresHendelseDao(dataSource)
    val behandlingshendelseDao = PostgresBehandlingshendelseDao(dataSource)

    return RapidApplication.create(environment).apply {
        SendtSøknadArbeidsgiverRiver(this, sendtSøknadDao)
        SendtSøknadNavRiver(this, sendtSøknadDao)
        VedtakFattetRiver(this, vedtakFattetDao)
        VedtakForkastetRiver(this, vedtakForkastetDao)
        BehandlingOpprettet.river(this, hendelseDao, behandlingshendelseDao)
        VedtakFattet.river(this, hendelseDao, behandlingshendelseDao)
        AvsluttetUtenVedtak.river(this, hendelseDao, behandlingshendelseDao)
        BehandlingForkastet.river(this, hendelseDao, behandlingshendelseDao)
        VedtaksperiodeEndretTilGodkjenning.river(this, hendelseDao, behandlingshendelseDao)
        VedtaksperiodeEndretTilVilkårsprøving.river(this, hendelseDao, behandlingshendelseDao)
        VedtaksperiodeAvvist.river(this, hendelseDao, behandlingshendelseDao, nom)
        VedtaksperiodeGodkjent.river(this, hendelseDao, behandlingshendelseDao, nom)
        VedtaksperiodeAnnullert.river(this, hendelseDao, behandlingshendelseDao)
        VedtaksperiodeVenterPåGodkjenning.river(this, hendelseDao, behandlingshendelseDao)
    }
}

fun LocalDateTime.toOsloOffset(): OffsetDateTime =
    this.atOffset(ZoneId.of("Europe/Oslo").rules.getOffset(this))

fun ZonedDateTime.toOsloTid(): LocalDateTime =
    this.withZoneSameInstant(ZoneId.of("Europe/Oslo")).toLocalDateTime()

