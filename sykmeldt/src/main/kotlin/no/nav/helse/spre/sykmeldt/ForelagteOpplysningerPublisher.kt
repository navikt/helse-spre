package no.nav.helse.spre.sykmeldt

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

class KafkaForelagteOpplysningerPublisher : ForelagteOpplysningerPublisher {
    override fun sendMelding(vedtaksperiodeId: UUID, forelagteOpplysningerMelding: ForelagteOpplysningerMelding) {
        TODO("Not yet implemented")
    }
}

data class ForelagteOpplysningerMelding(
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    val tidsstempel: LocalDateTime,
    val skatteinntekter: List<Skatteinntekt>
) {
    data class Skatteinntekt(val måned: YearMonth, val beløp: Double) {}
}
