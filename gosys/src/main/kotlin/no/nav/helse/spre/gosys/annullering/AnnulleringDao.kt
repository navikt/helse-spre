package no.nav.helse.spre.gosys.annullering

import java.time.LocalDate
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language

class AnnulleringDao(private val dataSource: DataSource) {

    fun lagre(annulleringMessage: AnnulleringMessage) {
        @Language("PostgreSQL")
        val query = """
            INSERT INTO annullering(id, utbetaling_id, fnr, organisasjonsnummer, fom, tom, saksbehandler_ident, saksbehandler_epost, person_fagsystem_id, arbeidsgiver_fagsystem_id, opprettet)
            values(:id, :utbetaling_id, :fnr, :organisasjonsnummer, :fom, :tom, :saksbehandler_ident, :saksbehandler_epost, :person_fagsystem_id, :arbeidsgiver_fagsystem_id, :opprettet) ON CONFLICT DO NOTHING"""
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "id" to annulleringMessage.hendelseId,
                        "utbetaling_id" to annulleringMessage.utbetalingId,
                        "fnr" to annulleringMessage.fødselsnummer,
                        "organisasjonsnummer" to annulleringMessage.organisasjonsnummer,
                        "fom" to annulleringMessage.fom,
                        "tom" to annulleringMessage.tom,
                        "saksbehandler_ident" to annulleringMessage.saksbehandlerIdent,
                        "saksbehandler_epost" to annulleringMessage.saksbehandlerEpost,
                        "person_fagsystem_id" to annulleringMessage.personFagsystemId,
                        "arbeidsgiver_fagsystem_id" to annulleringMessage.arbeidsgiverFagsystemId,
                        "opprettet" to annulleringMessage.dato,
                    )
                ).asExecute
            )
        }
    }

    fun finnAnnulleringHvisFinnes(fnr: String, organisasjonsnummer: String): List<EksisterendeAnnullering> {
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT * FROM annullering WHERE fnr = ? AND organisasjonsnummer = ?"
            return session.run(
                queryOf(
                    query,
                    fnr,
                    organisasjonsnummer
                ).map { EksisterendeAnnullering(it.localDate("fom"), it.localDate("tom")) }.asList
            )
        }
    }

    data class EksisterendeAnnullering(val fom: LocalDate, val tom: LocalDate)
}




