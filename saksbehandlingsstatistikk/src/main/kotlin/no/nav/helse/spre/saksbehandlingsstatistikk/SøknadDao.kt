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
            """INSERT INTO søknad(hendelse_id, dokument_id, mottatt_dato, registrert_dato, vedtak_fattet)
VALUES (:hendelseId, :dokumentId, :mottattDato, :registrertDato, :vedtakFattet)
ON CONFLICT (hendelse_id)
    DO UPDATE SET vedtaksperiode_id   = :vedtaksperiodeId,
                  saksbehandler_ident = :saksbehandlerIdent,
                  vedtak_fattet       = :vedtakFattet,
                  automatisk_behandling = :automatiskBehandling,
                  resultat              = :resultat 
   """
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "hendelseId" to søknad.søknadHendelseId,
                        "dokumentId" to søknad.søknadDokumentId,
                        "mottattDato" to søknad.rapportert,
                        "registrertDato" to søknad.registrertDato,
                        "vedtaksperiodeId" to søknad.vedtaksperiodeId,
                        "saksbehandlerIdent" to søknad.saksbehandlerIdent,
                        "vedtakFattet" to søknad.vedtakFattet,
                        "automatiskBehandling" to søknad.automatiskBehandling,
                        "resultat" to søknad.resultat,
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

