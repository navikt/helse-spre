package no.nav.helse.spre.sykmeldt

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.LoggerFactory

internal val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

fun main() {

    val rapidsConnection = launchApplication()
    rapidsConnection.start()
}

private fun launchApplication(): RapidsConnection {
    val env = System.getenv()
    val rapidsConnection = RapidApplication.create(env)
    rapidsConnection.apply { SkatteinntekterLagtTilGrunnRiver(this) }
    return rapidsConnection
}
