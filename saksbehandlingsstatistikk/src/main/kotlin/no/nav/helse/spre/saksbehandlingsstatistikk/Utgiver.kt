package no.nav.helse.spre.saksbehandlingsstatistikk

import net.logstash.logback.argument.StructuredArguments.keyValue
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

interface Utgiver {
    fun publiserStatistikk(statistikkEvent: StatistikkEvent)
}

class KafkaUtgiver (private val kafka : KafkaProducer<String, String>) : Utgiver {
    override fun publiserStatistikk(statistikkEvent: StatistikkEvent) {
        val log = LoggerFactory.getLogger(KafkaUtgiver::class.java)
        val sikretLogg = LoggerFactory.getLogger("tjenestekall")
        val eventString = objectMapper.writeValueAsString(statistikkEvent)
        kafka.send(
            ProducerRecord(
                "tbd.aapen-sykepenger-saksbehandlingsstatistikk_v2",
                "FNR",
                eventString
            )
        ) { _, _ ->
            log.info("Publiserte melding, {}", keyValue("behandlingId", statistikkEvent.behandlingId))
            sikretLogg.info("Publiserte melding:\n{}", eventString)
        }
    }
}

