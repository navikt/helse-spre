package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.spre.saksbehandlingsstatistikk.util.JsonUtil.asUuid
import java.time.LocalDateTime
import java.util.*

data class GodkjenningsBehovLøsningData(
    val vedtaksperiodeId: UUID,
    val saksbehandlerIdent: String,
    val vedtakFattet: LocalDateTime,
    val automatiskBehandling: Boolean,
) {
    fun anrik(søknad: Søknad) = søknad
        .saksbehandlerIdent(saksbehandlerIdent)
        .vedtakFattet(vedtakFattet)
        .automatiskBehandling(automatiskBehandling)
        .resultat("AVVIST")

    fun vedtaksperiodeId(it: UUID) = copy(vedtaksperiodeId = it)
    fun saksbehandlerIdent(it: String) = copy(saksbehandlerIdent = it)
    fun automatiskBehandling(it: Boolean) = copy(automatiskBehandling = it)

    companion object {
        fun fromJson(packet: JsonMessage) =
            GodkjenningsBehovLøsningData(
                vedtaksperiodeId = packet["vedtaksperiodeId"].asUuid(),
                saksbehandlerIdent = packet["@løsning.Godkjenning.saksbehandlerIdent"].asText(),
                vedtakFattet = packet["@løsning.Godkjenning.godkjenttidspunkt"].asLocalDateTime(),
                automatiskBehandling = packet["@løsning.Godkjenning.automatiskBehandling"].asBoolean(),
            )
    }
}
