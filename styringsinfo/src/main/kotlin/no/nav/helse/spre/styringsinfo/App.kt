package no.nav.helse.spre.styringsinfo

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal val log: Logger = LoggerFactory.getLogger("sprestyringsinfo")
internal val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val rapidsConnection = launchApplication(System.getenv())
    rapidsConnection.start()
}

fun launchApplication(environment: Map<String, String>): RapidsConnection {
    val dataSourceBuilder = DataSourceBuilder(environment)
    val dataSource = dataSourceBuilder.getDataSource()
    dataSourceBuilder.migrate()

    val sendtSøknadDao = SendtSøknadDao(dataSource)
    val vedtakFattetDao = VedtakFattetDao(dataSource)

    return RapidApplication.create(environment).apply {
        SendtSøknadArbeidsgiverRiver(this, sendtSøknadDao)
        SendtSøknadNavRiver(this, sendtSøknadDao)
        VedtakFattetRiver(this, vedtakFattetDao)
    }
}