package no.nav.helse.spre.saksbehandlingsstatistikk

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")

val objectMapper = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule())
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}

@KtorExperimentalAPI
fun main() {
    val env = Environment(System.getenv())
    launchApplication(env)
}

@KtorExperimentalAPI
fun launchApplication(env: Environment) {
    val dataSource = DataSourceBuilder(env.db).getMigratedDataSource()

    val dokumentDao = DokumentDao(dataSource)
    val koblingDao = KoblingDao(dataSource)
    val producer =
        KafkaProducer<String, String>(
            loadBaseConfig(env.raw).toProducerConfig()
        )
    val spreService = SpreService(producer, dokumentDao, koblingDao)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env.raw))
        .build()
        .apply {
            setupRivers(dokumentDao, spreService)
            start()
        }

}

internal fun RapidsConnection.setupRivers(
    dokumentDao: DokumentDao,
    spreService: SpreService
) {
    NyttDokumentRiver(this, dokumentDao)
    VedtaksperiodeEndretRiver(this, spreService)
    UtbetalingUtbetaltRiver(this, spreService)
    VedtakFattetRiver(this, spreService)
}
