package no.nav.helse.spre.saksbehandlingsstatistikk

import kotliquery.Row
import java.time.LocalDateTime
import java.util.*

data class Søknad(
    val søknadHendelseId: UUID,
    val søknadDokumentId: UUID,
    val rapportert: LocalDateTime,
    val registrertDato: LocalDateTime,
    val vedtaksperiodeId: UUID? = null,
    val saksbehandlerIdent: String? = null,
    val vedtakFattet: LocalDateTime? = null
) {
    fun vedtaksperiodeId(it: UUID) = copy(vedtaksperiodeId = it)
    fun saksbehandlerIdent(it: String) = copy(saksbehandlerIdent = it)
    fun vedtakFattet(it: LocalDateTime) = copy(vedtakFattet = it)

    companion object {
        fun fromSql(row: Row) =
            Søknad(
                søknadHendelseId = UUID.fromString(row.string("hendelse_id")),
                søknadDokumentId = UUID.fromString(row.string("dokument_id")),
                rapportert = row.localDateTime("mottatt_dato"),
                registrertDato = row.localDateTime("registrert_dato"),
                vedtaksperiodeId = row.stringOrNull("vedtaksperiode_id")?.let(UUID::fromString),
                saksbehandlerIdent = row.stringOrNull("saksbehandler_ident"),
                vedtakFattet = row.localDateTimeOrNull("vedtak_fattet")
            )

    }
}