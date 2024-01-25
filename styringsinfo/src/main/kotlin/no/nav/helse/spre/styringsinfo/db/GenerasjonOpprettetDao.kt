package no.nav.helse.spre.styringsinfo.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.domain.GenerasjonOpprettet
import no.nav.helse.spre.styringsinfo.toOsloOffset
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
        val query = """INSERT INTO generasjon_opprettet (aktorId, generasjonId, vedtaksperiodeId, type, avsender, meldingsreferanseId, innsendt, registrert, hendelseId)
            VALUES (:aktorId, :generasjonId, :vedtaksperiodeId, :type, :avsender, :meldingsreferanseId, :innsendt, :registrert, :hendelseId)
            ON CONFLICT DO NOTHING;""".trimIndent()

        tx.run(
            queryOf(
                query,
                mapOf(
                    "aktorId" to generasjonOpprettet.aktørId,
                    "generasjonId" to generasjonOpprettet.generasjonId,
                    "vedtaksperiodeId" to generasjonOpprettet.vedtaksperiodeId,
                    "type" to generasjonOpprettet.type,
                    "avsender" to generasjonOpprettet.kilde.avsender,
                    "meldingsreferanseId" to generasjonOpprettet.kilde.meldingsreferanseId,
                    "innsendt" to generasjonOpprettet.kilde.innsendt.toOsloOffset(),
                    "registrert" to generasjonOpprettet.kilde.registrert.toOsloOffset(),
                    "hendelseId" to generasjonOpprettet.hendelseId,
                )
            ).asUpdate
        )
    }

}