package no.nav.helse.spre.saksbehandlingsstatistikk

import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language

class SøknadDao(private val dataSource: DataSource) {

    fun upsertSøknad(søknad: Søknad) {
        @Language("PostgreSQL")
        val query =
            """INSERT INTO søknad(hendelse_id, dokument_id, mottatt_dato, registrert_dato)
VALUES (:hendelseId, :dokumentId, :mottattDato, :registrertDato)
ON CONFLICT (hendelse_id) DO UPDATE SET vedtaksperiode_id = :vedtaksperiodeId, saksbehandler_ident = :saksbehandlerIdent"""
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "hendelseId" to søknad.hendelseId,
                        "dokumentId" to søknad.dokumentId,
                        "mottattDato" to søknad.mottattDato,
                        "registrertDato" to søknad.registrertDato,
                        "vedtaksperiodeId" to søknad.vedtaksperiodeId,
                        "saksbehandlerIdent" to søknad.saksbehandlerIdent
                    )
                ).asUpdate
            )
        }
    }

    fun finnSøknad(hendelseIder: List<UUID>) = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT * FROM søknad WHERE hendelse_id = ANY((?)::uuid[])"
        session.run(
            queryOf(query, hendelseIder.joinToString(prefix = "{", postfix = "}", separator = ",") { it.toString() })
                .map(Søknad::fromSql).asSingle
        )
    }

    fun finnSøknad(vedtaksperiodeId: UUID) = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT * FROM søknad WHERE vedtaksperiode_id = ?"
        session.run(
            queryOf(query, vedtaksperiodeId)
                .map(Søknad::fromSql).asSingle
        )
    }
}

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
        fun fromEvent(it : NyttDokumentData) =
                Søknad(it.hendelseId, it.søknadId,  it.mottattDato, it.registrertDato)
    }
}
