package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.spre.saksbehandlingsstatistikk.util.JsonUtil.asUuid
import java.time.LocalDateTime
import java.util.*

data class VedtaksperiodeForkastetData(
    val vedtaksperiodeId: UUID,
    val vedtaksperiodeForkastet: LocalDateTime,
    val aktørId: String
) {
    fun vedtaksperiodeId(it: UUID) = copy(vedtaksperiodeId = it)


    companion object {
        fun fromJson(packet: JsonMessage) = VedtaksperiodeForkastetData(
            vedtaksperiodeId = packet["vedtaksperiodeId"].asUuid(),
            vedtaksperiodeForkastet = packet["@opprettet"].asLocalDateTime(),
            aktørId = packet["aktørId"].asText(),
        )
    }
}

