package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.teamsak.behandling.teamSakRebooted
import no.nav.helse.spre.styringsinfo.teamsak.localDateTimeOslo
import java.time.temporal.Temporal
import javax.sql.DataSource

internal class PostgresHendelseDao(private val dataSource: DataSource) : HendelseDao {

    private val teamSakRebooted by lazy { dataSource.teamSakRebooted }

    override fun lagre(hendelse: Hendelse) {
        sessionOf(dataSource).use { session ->
            val opprettet: Temporal = if (teamSakRebooted) hendelse.opprettet else hendelse.opprettet.localDateTimeOslo
            session.run(queryOf(
                "INSERT INTO hendelse(id, opprettet, type, data) values (:id, :opprettet, :type, :data::jsonb) ON CONFLICT DO NOTHING",
                mapOf("id" to hendelse.id, "opprettet" to opprettet, "type" to hendelse.type, "data" to hendelse.data.toString())
            ).asUpdate)
        }
    }
}