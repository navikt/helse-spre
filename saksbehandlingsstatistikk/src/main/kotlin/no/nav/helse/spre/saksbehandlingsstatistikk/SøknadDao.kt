package no.nav.helse.spre.saksbehandlingsstatistikk

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

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

