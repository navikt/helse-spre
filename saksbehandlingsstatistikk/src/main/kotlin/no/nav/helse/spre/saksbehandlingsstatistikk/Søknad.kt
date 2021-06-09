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
    val vedtakFattet: LocalDateTime? = null,
    val automatiskBehandling: Boolean? = null,
    val resultat: String? = null,
) {
    fun vedtaksperiodeId(it: UUID) = copy(vedtaksperiodeId = it)
    fun saksbehandlerIdent(it: String) = copy(saksbehandlerIdent = it)
    fun vedtakFattet(it: LocalDateTime) = copy(vedtakFattet = it)
    fun automatiskBehandling(it: Boolean?) = copy(automatiskBehandling = it)
    fun resultat(it: String) = copy(resultat = it)

    companion object {
        fun fromSql(row: Row): Søknad {
            val automatiskBehandling = row.boolean("automatisk_behandling")
            val wasNull = row.underlying.wasNull()

            return Søknad(
                søknadHendelseId = UUID.fromString(row.string("hendelse_id")),
                søknadDokumentId = UUID.fromString(row.string("dokument_id")),
                rapportert = row.localDateTime("mottatt_dato"),
                registrertDato = row.localDateTime("registrert_dato"),
                vedtaksperiodeId = row.stringOrNull("vedtaksperiode_id")?.let(UUID::fromString),
                saksbehandlerIdent = row.stringOrNull("saksbehandler_ident"),
                vedtakFattet = row.localDateTimeOrNull("vedtak_fattet"),
                automatiskBehandling = if (wasNull) null else automatiskBehandling,
                resultat = row.stringOrNull("resultat")
            )
        }
    }
}