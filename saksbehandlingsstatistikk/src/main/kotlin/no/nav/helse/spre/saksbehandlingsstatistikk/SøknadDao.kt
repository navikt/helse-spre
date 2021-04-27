package no.nav.helse.spre.saksbehandlingsstatistikk

import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language

class SøknadDao(private val dataSource: DataSource) {

    fun opprett(søknad: Søknad) {
        @Language("PostgreSQL")
        val query =
            "INSERT INTO søknad(hendelse_id, dokument_id, mottatt_dato, registrert_dato) VALUES(?,?,?,?) ON CONFLICT DO NOTHING"
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    søknad.hendelseId,
                    søknad.dokumentId,
                    søknad.mottattDato,
                    søknad.registrertDato
                ).asUpdate
            )
        }
    }

    fun settSaksbehandlerPåSøknad(saksbehandlerIdent: String, søknadId: UUID) {
        @Language("PostgreSQL")
        val query =
            "UPDATE søknad SET saksbehandler_ident = ? WHERE dokument_id = ?"
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    saksbehandlerIdent,
                    søknadId
                ).asUpdate
            )
        }
    }

    fun finnSøknad(hendelseIder: List<UUID>) = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT * FROM søknad WHERE hendelse_id = ANY((?)::uuid[])"
        session.run(
            queryOf(query, hendelseIder.joinToString(prefix = "{", postfix = "}", separator = ",") { it.toString() })
                .map { row ->
                    Søknad(
                        hendelseId = UUID.fromString(row.string("hendelse_id")),
                        dokumentId = UUID.fromString(row.string("dokument_id")),
                        mottattDato = row.localDateTime("mottatt_dato"),
                        registrertDato = row.localDateTime("registrert_dato"),
                        saksbehandlerIdent = row.stringOrNull("saksbehandler_ident")
                    )
                }.asSingle
        )
    }
}

data class Søknad(
    val hendelseId: UUID,
    val dokumentId: UUID,
    val mottattDato: LocalDateTime,
    val registrertDato: LocalDateTime,
    val saksbehandlerIdent: String?
)


