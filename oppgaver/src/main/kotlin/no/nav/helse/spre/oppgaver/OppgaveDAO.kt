package no.nav.helse.spre.oppgaver

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.spre.oppgaver.DatabaseTilstand.*
import no.nav.helse.spre.oppgaver.Oppgave.Tilstand
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS
import java.util.*
import javax.sql.DataSource

private enum class DatabaseTilstand {
    SpleisFerdigbehandlet,
    LagOppgave,
    LagOppgaveForSpeilsaksbehandlere,
    SpleisLest,
    DokumentOppdaget,
    KortInntektsmeldingFerdigbehandlet,
    KortSøknadFerdigbehandlet
}

class OppgaveDAO(private val dataSource: DataSource) {
    private val tilstandmapping = mapOf(
        SpleisFerdigbehandlet to Tilstand.SpleisFerdigbehandlet,
        LagOppgave to Tilstand.LagOppgave,
        LagOppgaveForSpeilsaksbehandlere to Tilstand.LagOppgaveForSpeilsaksbehandlere,
        SpleisLest to Tilstand.SpleisLest,
        DokumentOppdaget to Tilstand.DokumentOppdaget,
        KortInntektsmeldingFerdigbehandlet to Tilstand.KortInntektsmeldingFerdigbehandlet,
        KortSøknadFerdigbehandlet to Tilstand.KortSøknadFerdigbehandlet
    )

    private val dokumentmapping = mapOf(
        "Søknad" to DokumentType.Søknad,
        "Inntektsmelding" to DokumentType.Inntektsmelding
    )

    private fun fraDBTilstand(tilstand: String) = tilstandmapping.getValue(enumValueOf(tilstand))
    private fun Tilstand.toDBTilstand() = tilstandmapping.entries
        .single { (_, tilstand) -> this == tilstand }
        .key


    fun finnOppgave(hendelseId: UUID, observer: Oppgave.Observer) = sessionOf(dataSource).use { session ->
        session.run(
            queryOf("SELECT * FROM oppgave_tilstand WHERE hendelse_id=? LIMIT 1;", hendelseId)
                .map { rs -> mapTilOppgave(rs, observer) }
                .asSingle
        )
    }

    fun fjernOpgaver(fødselsnummer: String) = sessionOf(dataSource).use {
        @Language("PostgreSQL")
        val deleteStatement = "delete from oppgave_tilstand where fodselsnummer=?;"
        it.run(queryOf(deleteStatement, fødselsnummer).asExecute)
    }

    fun finnOppgaverIDokumentOppdaget(
        orgnummer: String,
        fødselsnummer: String,
        observer: OppgaveObserver,
        hendelser: List<UUID>
    ) = sessionOf(dataSource).use { session ->
        session.run(
            queryOf(
                "SELECT * FROM oppgave_tilstand WHERE tilstand = 'DokumentOppdaget' AND orgnummer=? AND fodselsnummer = ? AND hendelse_id NOT IN (${hendelser.joinToString { "?" }});",
                orgnummer,
                fødselsnummer,
                *hendelser.toTypedArray()
            )
                .map { rs -> mapTilOppgave(rs, observer) }
                .asList
        )
    }

    private fun mapTilOppgave(rs: Row, observer: Oppgave.Observer) = Oppgave(
        hendelseId = UUID.fromString(rs.string("hendelse_id")),
        dokumentId = UUID.fromString(rs.string("dokument_id")),
        fødselsnummer = rs.stringOrNull("fodselsnummer"),
        orgnummer = rs.stringOrNull("orgnummer"),
        tilstand = fraDBTilstand(rs.string("tilstand")),
        dokumentType = dokumentmapping.getValue(rs.string("dokument_type")),
        sistEndret = rs.localDateTimeOrNull("sist_endret"),
        observer = observer
    )

    fun opprettOppgaveHvisNy(
        hendelseId: UUID,
        dokumentId: UUID,
        fødselsnummer: String,
        orgnummer: String?,
        dokumentType: DokumentType
    ) =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "INSERT INTO oppgave_tilstand(hendelse_id, dokument_id, fodselsnummer, orgnummer, tilstand, dokument_type, sist_endret) VALUES(?, ?, ?, ?, 'DokumentOppdaget', CAST(? AS dokument_type), NOW()) ON CONFLICT (hendelse_id) DO NOTHING;",
                    hendelseId,
                    dokumentId,
                    fødselsnummer,
                    orgnummer,
                    dokumentmapping.entries.single { (_, dokument) -> dokument == dokumentType }.key
                ).asUpdate
            ) == 1
        }

    fun oppdaterTilstand(hendelseId: UUID, nyTilstand: Tilstand) = sessionOf(dataSource).use { session ->
        session.run(
            queryOf(
                "UPDATE oppgave_tilstand SET tilstand=CAST(? AS tilstand_type), sist_endret = NOW() WHERE hendelse_id=?;",
                nyTilstand.toDBTilstand().name,
                hendelseId
            ).asUpdate
        )
    }

    internal fun lagreTimeout(dokumentId: UUID, timeout: LocalDateTime) {
        sessionOf(dataSource).use { session ->
            session.run(queryOf("INSERT INTO timeout(dokumentId, timeout) VALUES(:dokumentId, :timeout) ON CONFLICT(dokumentId) DO UPDATE SET timeout = :timeout",
                mapOf("dokumentId" to dokumentId, "timeout" to timeout)
            ).asExecute)
        }
    }

    internal fun hentTimeout(dokumentId: UUID, foreslåttTimeout: LocalDateTime): LocalDateTime {
        val forrigeTimeout =  sessionOf(dataSource).use { session ->
            session.run(queryOf("SELECT timeout FROM timeout WHERE dokumentId = :dokumentId", mapOf("dokumentId" to dokumentId)).map { row ->
                row.localDateTime("timeout")
            }.asSingle)
        } ?: LocalDateTime.MIN

        return maxOf(forrigeTimeout, foreslåttTimeout).also { benyttetTimeout ->
            if (benyttetTimeout == foreslåttTimeout) log.info("Bruker foreslått timeout for {}, foreslåttTimeout=${foreslåttTimeout.truncatedTo(SECONDS)}, forrigeTimeout=${forrigeTimeout.takeUnless { it == LocalDateTime.MIN }?.truncatedTo(SECONDS)}", keyValue("dokumentId", dokumentId))
            else log.info("Bruker forrige timeout for {}, foreslåttTimeout=${foreslåttTimeout.truncatedTo(SECONDS)}, forrigeTimeout=${forrigeTimeout.takeUnless { it == LocalDateTime.MIN }?.truncatedTo(SECONDS)}", keyValue("dokumentId", dokumentId))
        }
    }
}