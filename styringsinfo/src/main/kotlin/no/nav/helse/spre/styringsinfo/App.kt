package no.nav.helse.spre.styringsinfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
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

fun main() {
    val environment = System.getenv()
    val dataSourceBuilder = DataSourceBuilder(environment)
    val dataSource = dataSourceBuilder.getDataSource()
    val sendtSøknadPatcher = SendtSøknadPatcher(SendtSøknadDao(dataSource))
    dataSourceBuilder.migrate()
    thread {
        sendtSøknadPatcher.patchSendtSøknad(
            patchLevelMindreEnn = 1,
            initialSleepMillis = 60_000,
            loopSleepMillis = 10_000
        )
    }
    val rapidsConnection = launchApplication(dataSource, environment)
    rapidsConnection.start()
}

fun launchApplication(dataSource: HikariDataSource, environment: MutableMap<String, String>): RapidsConnection {
    val sendtSøknadDao = SendtSøknadDao(dataSource)
    val vedtakFattetDao = VedtakFattetDao(dataSource)
    val vedtakForkastetDao = VedtakForkastetDao(dataSource)

    return RapidApplication.create(environment).apply {
        SendtSøknadArbeidsgiverRiver(this, sendtSøknadDao)
        SendtSøknadNavRiver(this, sendtSøknadDao)
        VedtakFattetRiver(this, vedtakFattetDao)
        VedtakForkastetRiver(this, vedtakForkastetDao)
    }
}


fun LocalDateTime.toOsloOffset(): OffsetDateTime =
    this.atOffset(ZoneId.of("Europe/Oslo").rules.getOffset(this))

fun ZonedDateTime.toOsloTid(): LocalDateTime =
    this.withZoneSameInstant(ZoneId.of("Europe/Oslo")).toLocalDateTime()