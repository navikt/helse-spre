package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.spre.saksbehandlingsstatistikk.util.JsonUtil.asUuid
import java.util.*

data class VedtakFattetData(
    val aktørId: String,
    val hendelser: List<UUID>,
    val vedtaksperiodeId: UUID,
) {
    fun hendelse(it: UUID) = copy(hendelser = hendelser + it)


    companion object {
        fun fromJson(packet: JsonMessage) = VedtakFattetData(
            aktørId = packet["aktørId"].asText(),
            hendelser = packet["hendelser"].map { it.asUuid() },
            vedtaksperiodeId = packet["vedtaksperiodeId"].asUuid()
        )
    }
}
