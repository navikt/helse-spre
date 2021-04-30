package no.nav.helse.spre.saksbehandlingsstatistikk

import kotliquery.Row
import java.time.LocalDateTime
import java.util.*

data class Søknad(
    val hendelseId: UUID,
    val dokumentId: UUID,
    val mottattDato: LocalDateTime,
    val registrertDato: LocalDateTime,
    val vedtaksperiodeId: UUID? = null,
    val saksbehandlerIdent: String? = null
) {
    fun vedtaksperiodeId(it: UUID) = copy(vedtaksperiodeId = it)
    fun saksbehandlerIdent(it: String) = copy(saksbehandlerIdent = it)

    companion object {
        fun fromSql(row: Row) =
            Søknad(
                hendelseId = UUID.fromString(row.string("hendelse_id")),
                dokumentId = UUID.fromString(row.string("dokument_id")),
                mottattDato = row.localDateTime("mottatt_dato"),
                registrertDato = row.localDateTime("registrert_dato"),
                vedtaksperiodeId = row.stringOrNull("vedtaksperiode_id")?.let(UUID::fromString),
                saksbehandlerIdent = row.stringOrNull("saksbehandler_ident")
            )

    }
}