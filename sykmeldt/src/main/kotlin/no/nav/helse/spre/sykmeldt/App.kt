package no.nav.helse.spre.sykmeldt

import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import org.slf4j.LoggerFactory

internal val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val rapidsConnection = launchApplication()
    rapidsConnection.start()
}

private fun launchApplication(): RapidsConnection {
    val env = System.getenv()
    val factory = ConsumerProducerFactory(AivenConfig.default)
    val producer = factory.createProducer()
    val publisher = KafkaForelagteOpplysningerPublisher(producer)
    val rapidsConnection = RapidApplication.create(env, factory)
    rapidsConnection.apply { SkatteinntekterLagtTilGrunnRiver(this, publisher) }
    return rapidsConnection
}

