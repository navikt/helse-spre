package no.nav.helse.spre.saksbehandlingsstatistikk

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.util.*
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")

val objectMapper = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule())
    this.setSerializationInclusion(NON_NULL)
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}


@KtorExperimentalAPI
fun main() {
    val env = Environment(System.getenv())
    launchApplication(env)
}

@KtorExperimentalAPI
fun launchApplication(env: Environment) {
    global.setVersjon()
    log.info("Starter spre-saksbehandlingsstatistikk versjon ${global.versjon}")
    val dataSource = DataSourceBuilder(env.db).getMigratedDataSource()
    val søknadDao = SøknadDao(dataSource)
    val producer =
        KafkaProducer<String, String>(
            loadBaseConfig(env.raw).toProducerConfig()
        )
    val annonsør= KafkaUtgiver(producer)
    val spreService = SpreService(annonsør, søknadDao)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env.raw))
        .build()
        .apply {
            setupRivers(spreService, søknadDao)
            start()
        }

}

internal fun RapidsConnection.setupRivers(
    spreService: SpreService,
    søknadDao: SøknadDao,
) {
    SøknadRiver(this, søknadDao)
    VedtaksperiodeEndretRiver(this, søknadDao)
    VedtaksperiodeGodkjentRiver(this, søknadDao)
    VedtaksperiodeAvvistRiver(this, søknadDao)
    GodkjenningsBehovLøsningRiver(this, søknadDao)
    VedtakFattetRiver(this, spreService, søknadDao)
    VedtaksperiodeForkastetRiver(this, spreService, søknadDao)
}
