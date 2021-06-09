package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.spre.saksbehandlingsstatistikk.util.JsonUtil.asUuid
import java.time.LocalDateTime
import java.util.*

data class VedtakFattetData(
    val aktørId: String,
    val hendelser: List<UUID>,
    val vedtaksperiodeId: UUID,
    val avsluttetISpleis: LocalDateTime,
    val erAvsluttetUtenGodkjenning: Boolean
) {
    fun hendelse(it: UUID) = copy(hendelser = hendelser + it)
    fun vedtaksperiodeId(it: UUID) = copy(vedtaksperiodeId = it)

    fun anrik(søknad: Søknad) = søknad.vedtakFattet(avsluttetISpleis).resultat("INNVILGET")
        .let {
            when (erAvsluttetUtenGodkjenning) {
                true -> it.saksbehandlerIdent("SPLEIS").automatiskBehandling(true)
                else -> it
            }
        }

    companion object {
        fun fromJson(packet: JsonMessage) = VedtakFattetData(
            aktørId = packet["aktørId"].asText(),
            hendelser = packet["hendelser"].map { it.asUuid() },
            vedtaksperiodeId = packet["vedtaksperiodeId"].asUuid(),
            avsluttetISpleis = packet["@opprettet"].asLocalDateTime(),
            erAvsluttetUtenGodkjenning = packet["@forårsaket_av.event_name"].asText() == "sendt_søknad_arbeidsgiver"
        )
    }
}
