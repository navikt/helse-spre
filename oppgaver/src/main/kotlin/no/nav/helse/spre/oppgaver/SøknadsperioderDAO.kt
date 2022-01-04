package no.nav.helse.spre.oppgaver

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import java.util.*
import javax.sql.DataSource

class SøknadsperioderDAO(private val dataSource: DataSource) {

    fun lagre(hendelseId: UUID, periode: Periode) = using(sessionOf(dataSource)) { session ->
        session.run(
            queryOf(
                "INSERT INTO søknadsperioder(hendelse_id, fom, tom) VALUES(?, ?, ?) ON CONFLICT (hendelse_id) DO NOTHING;",
                hendelseId,
                periode.first,
                periode.second
            ).asUpdate
        )
    }

    fun finn(hendelseId: UUID) = using(sessionOf(dataSource)) { session ->
        session.run(
            queryOf(
                "SELECT * FROM søknadsperioder WHERE hendelse_id = ?",
                hendelseId
            ).map { Periode(it.localDate("fom"), it.localDate("tom")) }.asSingle
        )
    }
}