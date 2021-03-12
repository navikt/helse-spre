package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.spre.saksbehandlingsstatistikk.BehandlingStatus.REGISTRERT
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

internal class SpreService(
    private val statistikkProducer: KafkaProducer<String, String>,
    private val dokumentDao: DokumentDao
) {
    internal fun spre(vedtaksperiodeEndretData: VedtaksperiodeEndretData) {
        val dokumenter = dokumentDao.finnDokumenter(vedtaksperiodeEndretData.hendelser)
        val statistikkEvent: StatistikkEvent = vedtaksperiodeEndretData.toStatistikkEvent()
        statistikkProducer.send(ProducerRecord(
            "aapen-sykepenger-saksbehandlingsstatistikk",
            null,
            "FNR",
            objectMapper.writeValueAsString(statistikkEvent)
        ))
    }

    private fun VedtaksperiodeEndretData.toStatistikkEvent() = StatistikkEvent(
        aktorId =  "aktorId",
        behandlingStatus = REGISTRERT
    )
}
