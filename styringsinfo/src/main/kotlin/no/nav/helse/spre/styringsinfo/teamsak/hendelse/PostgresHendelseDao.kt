package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import kotliquery.queryOf
import kotliquery.sessionOf
import javax.sql.DataSource

internal class PostgresHendelseDao(private val dataSource: DataSource): HendelseDao {

    override fun lagre(hendelse: Hendelse) = sessionOf(dataSource).use { session ->
        session.run(queryOf(
            "INSERT INTO hendelse(id, opprettet, type, data) values (:id, :opprettet, :type, :data::jsonb) ON CONFLICT DO NOTHING",
            mapOf("id" to hendelse.id, "opprettet" to hendelse.opprettet, "type" to hendelse.type, "data" to hendelse.data.toString())
        ).asUpdate) == 1
    }
}