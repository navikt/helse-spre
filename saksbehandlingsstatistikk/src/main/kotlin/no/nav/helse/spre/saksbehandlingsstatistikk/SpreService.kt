package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.spre.saksbehandlingsstatistikk.BehandlingStatus.REGISTRERT
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

internal class SpreService(
    private val statistikkProducer: KafkaProducer<String, String>,
    private val dokumentDao: DokumentDao
) {
    private val log = LoggerFactory.getLogger(SpreService::class.java)

    internal fun spre(vedtaksperiodeEndretData: VedtaksperiodeEndretData) {
        val dokumenter = dokumentDao.finnDokumenter(vedtaksperiodeEndretData.hendelser)
        val statistikkEvent: StatistikkEvent = vedtaksperiodeEndretData.toStatistikkEvent(dokumenter)
        val eventString = objectMapper.writeValueAsString(statistikkEvent)
        statistikkProducer.send(ProducerRecord(
            "aapen-sykepenger-saksbehandlingsstatistikk-tulletopic",
            "FNR",
            eventString
        ))
        log.info("Publisert melding på tulletopic {}", eventString)
    }

    private fun VedtaksperiodeEndretData.toStatistikkEvent(dokumenter: Dokumenter) = StatistikkEvent(
        aktorId =  "aktorId",
        behandlingStatus = REGISTRERT,
        behandlingId = dokumenter.søknad?.dokumentId
    )
}
