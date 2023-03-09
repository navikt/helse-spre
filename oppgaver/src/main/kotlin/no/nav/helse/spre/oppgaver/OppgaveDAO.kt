package no.nav.helse.spre.oppgaver

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import java.util.*
import javax.sql.DataSource

enum class DatabaseTilstand {
    SpleisFerdigbehandlet,
    LagOppgave,
    LagOppgaveForSpeilsaksbehandlere,
    SpleisLest,
    DokumentOppdaget,
    KortInntektsmeldingFerdigbehandlet,
    KortSøknadFerdigbehandlet
}

class OppgaveDAO(private val dataSource: DataSource) {
    fun finnOppgave(hendelseId: UUID): Oppgave? = sessionOf(dataSource).use { session ->
        session.run(queryOf(
            "SELECT * FROM oppgave_tilstand WHERE hendelse_id=?;",
            hendelseId
        )
            .map { rs ->
                Oppgave(
                    hendelseId = UUID.fromString(rs.stringOrNull("hendelse_id")),
                    dokumentId = UUID.fromString(rs.stringOrNull("dokument_id")),
                    fødselsnummer = rs.string("fodselsnummer"),
                    orgnummer = rs.string("orgnummer"),
                    tilstand = when (enumValueOf<DatabaseTilstand>(rs.string("tilstand"))) {
                        DatabaseTilstand.SpleisFerdigbehandlet -> Oppgave.Tilstand.SpleisFerdigbehandlet
                        DatabaseTilstand.LagOppgave -> Oppgave.Tilstand.LagOppgave
                        DatabaseTilstand.LagOppgaveForSpeilsaksbehandlere -> Oppgave.Tilstand.LagOppgaveForSpeilsaksbehandlere
                        DatabaseTilstand.SpleisLest -> Oppgave.Tilstand.SpleisLest
                        DatabaseTilstand.DokumentOppdaget -> Oppgave.Tilstand.DokumentOppdaget
                        DatabaseTilstand.KortInntektsmeldingFerdigbehandlet -> Oppgave.Tilstand.KortInntektsmeldingFerdigbehandlet
                        DatabaseTilstand.KortSøknadFerdigbehandlet -> Oppgave.Tilstand.KortSøknadFerdigbehandlet
                    },
                    dokumentType = DokumentType.valueOf(rs.string("dokument_type")),
                    sistEndret = rs.localDateTimeOrNull("sist_endret")
                )
            }
            .asSingle
        )
    }

    fun opprettOppgaveHvisNy(hendelseId: UUID, dokumentId: UUID, fødselsnummer: String, orgnummer: String, dokumentType: DokumentType) =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "INSERT INTO oppgave_tilstand(hendelse_id, dokument_id, fodselsnummer, orgnummer, dokument_type, sist_endret) VALUES(?, ?, ?, ?, CAST(? AS dokument_type), NOW()) ON CONFLICT (hendelse_id) DO NOTHING;",
                    hendelseId,
                    dokumentId,
                    fødselsnummer,
                    orgnummer,
                    dokumentType.name
                ).asUpdate
            )
        }

    fun oppdaterTilstand(oppgave: Oppgave) = sessionOf(dataSource).use { session ->
        session.run(
            queryOf(
                "UPDATE oppgave_tilstand SET tilstand=CAST(? AS tilstand_type), sist_endret = NOW() WHERE hendelse_id=?;",
                oppgave.tilstand.toDBTilstand().name, oppgave.hendelseId
            ).asUpdate
        )
    }

    fun lagreVedtaksperiodeEndretTilInfotrygd(hendelseId: UUID) = sessionOf(dataSource).use{ session ->
        session.run(
            queryOf(
                "INSERT INTO vedtaksperiode_endret_tilinfotrygd(hendelse_id, sist_endret) VALUES(?, NOW()) ON CONFLICT (hendelse_id) DO NOTHING;",
                hendelseId
            ).asUpdate
        )
    }

    fun markerSomUtbetalingTilSøker(dokumentId: UUID) =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "INSERT INTO utbetaling_til_søker(dokument_id) VALUES(?) ON CONFLICT (dokument_id) DO NOTHING;",
                    dokumentId,
                ).asUpdate
            )
        }

    fun harUtbetalingTilSøker(dokumentId: UUID): Boolean = sessionOf(dataSource).use { session ->
        session.run(queryOf(
            "SELECT COUNT(1) FROM utbetaling_til_søker WHERE dokument_id=?;",
            dokumentId
        ).map { it.int(1) }.asSingle
        )
    } == 1
}

private fun Oppgave.Tilstand.toDBTilstand(): DatabaseTilstand = when (this) {
    Oppgave.Tilstand.SpleisFerdigbehandlet -> DatabaseTilstand.SpleisFerdigbehandlet
    Oppgave.Tilstand.LagOppgave -> DatabaseTilstand.LagOppgave
    Oppgave.Tilstand.LagOppgaveForSpeilsaksbehandlere -> DatabaseTilstand.LagOppgaveForSpeilsaksbehandlere
    Oppgave.Tilstand.SpleisLest -> DatabaseTilstand.SpleisLest
    Oppgave.Tilstand.DokumentOppdaget -> DatabaseTilstand.DokumentOppdaget
    Oppgave.Tilstand.KortInntektsmeldingFerdigbehandlet -> DatabaseTilstand.KortInntektsmeldingFerdigbehandlet
    Oppgave.Tilstand.KortSøknadFerdigbehandlet -> DatabaseTilstand.KortSøknadFerdigbehandlet
}
