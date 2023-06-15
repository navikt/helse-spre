package no.nav.helse.spre.styringsinfo

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

internal val log: Logger = LoggerFactory.getLogger("sprestyringsinfo")
internal val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

internal fun JsonNode.asUuid() = UUID.fromString(this.asText())

fun main() {
    val rapidsConnection = launchApplication(System.getenv())
    rapidsConnection.start()
}

fun launchApplication(environment: Map<String, String>): RapidsConnection {
    val dataSourceBuilder = DataSourceBuilder(environment)
    val dataSource = dataSourceBuilder.getDataSource()
    dataSourceBuilder.migrate()

    val sendtSøknadDao = SendtSøknadDao(dataSource)

    return RapidApplication.create(environment).apply {
        SendtSøknadRiver(this, sendtSøknadDao)
    }
}