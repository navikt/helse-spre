package no.nav.helse.spre.styringsinfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.db.*
import no.nav.helse.spre.styringsinfo.db.DataSourceBuilder
import no.nav.helse.spre.styringsinfo.domain.SendtSøknadPatch
import no.nav.helse.spre.styringsinfo.domain.VedtakFattetPatch
import no.nav.helse.spre.styringsinfo.domain.VedtakForkastetPatch
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.*
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.AvsluttetMedVedtak
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.AvsluttetUtenVedtak
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.GenerasjonOpprettet
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

    val rapidsConnection = launchApplication(dataSource, environment)
    rapidsConnection.start()
}

fun launchApplication(dataSource: HikariDataSource, environment: MutableMap<String, String>): RapidsConnection {
    val sendtSøknadDao = SendtSøknadDao(dataSource)
    val vedtakFattetDao = VedtakFattetDao(dataSource)
    val vedtakForkastetDao = VedtakForkastetDao(dataSource)
    val generasjonOpprettetDao = GenerasjonOpprettetDao(dataSource)

    val tulleBehandlingDao: BehandlingDao = object: BehandlingDao {
        override fun initialiser(behandlingId: BehandlingId): Behandling.Builder? = null
        override fun lagre(behandling: Behandling) {}
        override fun hent(behandlingId: BehandlingId) = null
        override fun forrigeBehandlingId(sakId: SakId) = null
    }

    return RapidApplication.create(environment).apply {
        SendtSøknadArbeidsgiverRiver(this, sendtSøknadDao)
        SendtSøknadNavRiver(this, sendtSøknadDao)
        VedtakFattetRiver(this, vedtakFattetDao)
        VedtakForkastetRiver(this, vedtakForkastetDao)
        GenerasjonOpprettetRiver(this, generasjonOpprettetDao)
        GenerasjonOpprettet.river(this, tulleBehandlingDao)
        AvsluttetMedVedtak.river(this, tulleBehandlingDao)
        AvsluttetUtenVedtak.river(this, tulleBehandlingDao)
        GenerasjonForkastet.river(this, tulleBehandlingDao)
    }
}

fun LocalDateTime.toOsloOffset(): OffsetDateTime =
    this.atOffset(ZoneId.of("Europe/Oslo").rules.getOffset(this))

fun ZonedDateTime.toOsloTid(): LocalDateTime =
    this.withZoneSameInstant(ZoneId.of("Europe/Oslo")).toLocalDateTime()

