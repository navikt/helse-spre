package no.nav.helse.spre.styringsinfo.teamsak

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Hendelse
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseDao
import javax.sql.DataSource

internal class PostgresHendelseDao(private val dataSource: DataSource): HendelseDao {

    override fun lagre(hendelse: Hendelse) {
        sessionOf(dataSource).use { session ->
            session.run(queryOf(
                "INSERT INTO hendelse(id, opprettet, type, data) values (:id, :opprettet, :type, :data::jsonb) ON CONFLICT DO NOTHING",
                mapOf("id" to hendelse.id, "opprettet" to hendelse.opprettet, "type" to hendelse.type, "data" to hendelse.data.toString())
            ).asExecute)
        }
    }
}