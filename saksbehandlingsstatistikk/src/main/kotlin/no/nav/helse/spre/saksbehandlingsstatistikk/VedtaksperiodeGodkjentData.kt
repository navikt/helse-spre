package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.spre.saksbehandlingsstatistikk.util.JsonUtil.asUuid
import java.time.LocalDateTime
import java.util.*

data class VedtaksperiodeGodkjentData(
    val vedtaksperiodeId: UUID,
    val saksbehandlerIdent: String,
    val vedtakFattet: LocalDateTime,
    val automatiskBehandling: Boolean,
) {

    fun anrik(søknad: Søknad) = søknad
        .saksbehandlerIdent(saksbehandlerIdent)
        .vedtakFattet(vedtakFattet)
        .automatiskBehandling(automatiskBehandling)
        .resultat("INNVILGET")

    fun automatiskBehandling(it: Boolean) = copy(automatiskBehandling = it)

    companion object {
        fun fromJson(packet: JsonMessage) =
            VedtaksperiodeGodkjentData(
                vedtaksperiodeId = packet["vedtaksperiodeId"].asUuid(),
                saksbehandlerIdent = packet["saksbehandlerIdent"].asText(),
                vedtakFattet = packet["@opprettet"].asLocalDateTime(),
                automatiskBehandling = packet["automatiskBehandling"].asBoolean(),
            )
    }
}

