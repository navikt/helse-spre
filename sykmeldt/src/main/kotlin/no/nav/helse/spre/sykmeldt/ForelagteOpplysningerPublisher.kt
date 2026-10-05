package no.nav.helse.spre.sykmeldt

import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.module.kotlin.jacksonMapperBuilder
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

interface ForelagteOpplysningerPublisher {
    fun sendMelding(vedtaksperiodeId: UUID, forelagteOpplysningerMelding: ForelagteOpplysningerMelding)
}

class TestForelagteOpplysningerPublisher : ForelagteOpplysningerPublisher {
    val sendteMeldinger: MutableList<ForelagteOpplysningerMelding> = mutableListOf()
    override fun sendMelding(vedtaksperiodeId: UUID, forelagteOpplysningerMelding: ForelagteOpplysningerMelding) {
        sendteMeldinger.add(forelagteOpplysningerMelding)
    }

    fun harSendtMelding(vedtaksperiodeId: UUID): Boolean {
        return sendteMeldinger.any { it.vedtaksperiodeId == vedtaksperiodeId }
    }
}

class KafkaForelagteOpplysningerPublisher(private val producer: KafkaProducer<String, String>) : ForelagteOpplysningerPublisher {
    override fun sendMelding(vedtaksperiodeId: UUID, forelagteOpplysningerMelding: ForelagteOpplysningerMelding) {
        val json = mapper.writeValueAsString(forelagteOpplysningerMelding)
        producer.send(ProducerRecord(TOPICNAME, vedtaksperiodeId.toString(), json))
        sikkerlogg.info("Sendte melding på $TOPICNAME: \n $json")
    }

    companion object {
        val TOPICNAME = "tbd.forelagte-opplysninger"
        val mapper = jacksonMapperBuilder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()
    }
}

data class ForelagteOpplysningerMelding(
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    val skjæringstidspunkt: LocalDate,
    val tidsstempel: LocalDateTime,
    val omregnetÅrsinntekt: Double,
    val skatteinntekter: List<Skatteinntekt>
) {
    data class Skatteinntekt(val måned: YearMonth, val beløp: Double) {}
}
