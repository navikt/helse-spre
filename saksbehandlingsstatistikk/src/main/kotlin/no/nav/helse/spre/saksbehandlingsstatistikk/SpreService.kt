package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.spre.saksbehandlingsstatistikk.BehandlingStatus.AVSLUTTET
import no.nav.helse.spre.saksbehandlingsstatistikk.BehandlingType.SØKNAD
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

internal class SpreService(
    private val statistikkProducer: KafkaProducer<String, String>,
    private val koblingDao: KoblingDao,
    private val søknadDao: SøknadDao
) {
    private val log = LoggerFactory.getLogger(SpreService::class.java)

    internal fun spre(vedtakFattetData: VedtakFattetData) {
        val søknad = requireNotNull(søknadDao.finnSøknad(vedtakFattetData.hendelser)) { "Finner ikke søknad for vedtak_fattet" }
        koblingDao.opprettSøknadKobling(søknad.dokumentId, vedtakFattetData.utbetalingId,vedtakFattetData.vedtaksperiodeId )
        sendEvent(vedtakFattetData.toStatistikkEvent(søknad))
    }

    private fun VedtakFattetData.toStatistikkEvent(søknad: Søknad) = StatistikkEvent(
        aktorId = aktørId,
        behandlingStatus = AVSLUTTET,
        behandlingId = søknad.dokumentId,
        behandlingType = SØKNAD,
        funksjonellTid = opprettet,
        mottattDato = søknad.mottattDato.toString(),
        registrertDato = søknad.registrertDato.toString(),
        saksbehandlerIdent = søknad.saksbehandlerIdent
    )

    private fun sendEvent(statistikkEvent: StatistikkEvent) {
        val eventString = objectMapper.writeValueAsString(statistikkEvent)
        statistikkProducer.send(
            ProducerRecord(
                "tbd.aapen-sykepenger-saksbehandlingsstatistikk-utviklingstopic",
                "FNR",
                eventString
            )
        ) { _, _ -> log.info("Publiserte melding på utviklingtopic: {}", eventString) }
    }
}

