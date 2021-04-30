package no.nav.helse.spre.saksbehandlingsstatistikk

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

internal class SpreService(
    private val statistikkProducer: KafkaProducer<String, String>,
    private val søknadDao: SøknadDao
) {

    internal fun spre(vedtakFattetData: VedtakFattetData) {
        val søknad =
            requireNotNull(søknadDao.finnSøknad(vedtakFattetData.hendelser)) { "Finner ikke søknad for vedtak_fattet" }
        statistikkProducer.sendEvent(StatistikkEvent.toStatistikkEvent(søknad, vedtakFattetData))
    }

    companion object {
        private fun KafkaProducer<String, String>.sendEvent(statistikkEvent: StatistikkEvent) {
            val log = LoggerFactory.getLogger(SpreService::class.java)
            val eventString = objectMapper.writeValueAsString(statistikkEvent)
            send(
                ProducerRecord(
                    "tbd.aapen-sykepenger-saksbehandlingsstatistikk-utviklingstopic",
                    "FNR",
                    eventString
                )
            ) { _, _ -> log.info("Publiserte melding på utviklingtopic: {}", eventString) }
        }
    }
}

