package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.spre.saksbehandlingsstatistikk.util.JsonUtil.asUuid
import java.time.LocalDateTime
import java.util.*

data class VedtakFattetData(
    val opprettet: LocalDateTime,
    val aktørId: String,
    val hendelser: List<UUID>,
    val vedtaksperiodeId: UUID,
) {


    companion object {
        fun fromJson(packet: JsonMessage) = VedtakFattetData(
            opprettet = packet["@opprettet"].asLocalDateTime(),
            aktørId = packet["aktørId"].asText(),
            hendelser = packet["hendelser"].map { it.asUuid() },
            vedtaksperiodeId = packet["vedtaksperiodeId"].asUuid()
        )
    }
}
