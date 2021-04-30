package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.spre.saksbehandlingsstatistikk.util.JsonUtil.asUuid
import java.util.*

data class VedtaksperiodeGodkjentData(
    val vedtaksperiodeId: UUID,
    val saksbehandlerIdent: String
) {
    companion object {
        fun fromJson(packet: JsonMessage) =
            VedtaksperiodeGodkjentData(
                vedtaksperiodeId = packet["vedtaksperiodeId"].asUuid(),
                saksbehandlerIdent = packet["saksbehandlerIdent"].asText()
            )
    }
}

