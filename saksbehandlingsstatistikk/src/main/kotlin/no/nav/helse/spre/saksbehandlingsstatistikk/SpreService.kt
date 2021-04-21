package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.spre.saksbehandlingsstatistikk.BehandlingStatus.REGISTRERT
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

internal class SpreService(
    private val statistikkProducer: KafkaProducer<String, String>,
    private val dokumentDao: DokumentDao
) {
    private val log = LoggerFactory.getLogger(SpreService::class.java)

    internal fun spre(vedtaksperiodeEndretData: VedtaksperiodeEndretData) {
        val søknadDokumentId = dokumentDao.finnSøknadDokumentId(vedtaksperiodeEndretData.hendelser)
        val statistikkEvent: StatistikkEvent = vedtaksperiodeEndretData.toStatistikkEvent(søknadDokumentId)
        val eventString = objectMapper.writeValueAsString(statistikkEvent)

        statistikkProducer.send(
            ProducerRecord(
                "tbd.aapen-sykepenger-saksbehandlingsstatistikk-utviklingstopic",
                "FNR",
                eventString
            )
        ) { _, _ -> log.info("Publiserte melding på tulletopic: {}", eventString) }
    }

    private fun VedtaksperiodeEndretData.toStatistikkEvent(søknadDokumentId: UUID?) = StatistikkEvent(
        aktorId = aktørId,
        behandlingStatus = REGISTRERT,
        behandlingId = søknadDokumentId
    )
}
