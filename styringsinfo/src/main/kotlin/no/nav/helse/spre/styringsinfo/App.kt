package no.nav.helse.spre.styringsinfo

import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.concurrent.thread

internal val log: Logger = LoggerFactory.getLogger("sprestyringsinfo")
internal val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val environment = System.getenv()
    val dataSourceBuilder = DataSourceBuilder(environment)
    val dataSource = dataSourceBuilder.getDataSource()
    dataSourceBuilder.migrate()

    val sendtSøknadDao = SendtSøknadDao(dataSource)
    thread {
        sleep(60_000)
        val antallSøknader = sendtSøknadDao.tellSøknaderSomFeiler()
        log.info("Kjører jobb i egen tråd og teller ${antallSøknader} søknader.")
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