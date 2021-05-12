package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.spre.saksbehandlingsstatistikk.util.JsonUtil.asUuid
import java.util.*

data class VedtaksperiodeEndretData(
    val hendelser: List<UUID>,
    val vedtaksperiodeId: UUID,
) {
    fun hendelse(it: UUID) = copy(hendelser = hendelser + it)

    companion object {
        fun fromJson(packet: JsonMessage) = VedtaksperiodeEndretData(
            hendelser = packet["hendelser"].map { UUID.fromString(it.asText()) },
            vedtaksperiodeId = packet["vedtaksperiodeId"].asUuid(),
        )
    }
}

