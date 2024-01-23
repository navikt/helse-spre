package no.nav.helse.spre.styringsinfo.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.domain.GenerasjonOpprettet
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

class GenerasjonOpprettetDao(private val dataSource: DataSource) {

    fun lagre(generasjonOpprettet: GenerasjonOpprettet) =
        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                lagreGenerasjonOpprettet(generasjonOpprettet, tx)
            }
        }

    private fun lagreGenerasjonOpprettet(generasjonOpprettet: GenerasjonOpprettet, tx: TransactionalSession) {
        @Language("PostgreSQL")
        val query = """INSERT INTO generasjon_opprettet (fodselsnummer, aktorId, organisasjonsnummer, vedtaksperiodeId, generasjonsId, type, meldingsreferanseId, innsendt, registrert, avsender)
            VALUES (:fodselsnummer, :aktorId, :organisasjonsnummer, :vedtaksperiodeId, :generasjonsId, :type, :meldingsreferanseId, :innsendt, :registrert, :avsender)
            ON CONFLICT DO NOTHING;""".trimIndent()

        tx.run(
            queryOf(
                query,
                mapOf(
                        "fodselsnummer" to generasjonOpprettet.fødselsnummer,
                )
            ).asUpdate
        )
    }

}