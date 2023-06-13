package no.nav.helse.spre.styringsinfo

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection

fun main() {
    val rapidsConnection = launchApplication(System.getenv())
    rapidsConnection.start()
}

fun launchApplication(environment: Map<String, String>): RapidsConnection =
    RapidApplication.create(environment).apply {

    }