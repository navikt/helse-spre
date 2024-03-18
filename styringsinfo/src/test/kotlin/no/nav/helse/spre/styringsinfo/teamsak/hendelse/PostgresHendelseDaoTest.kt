package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class PostgresHendelseDaoTest: AbstractDatabaseTest() {

    private val hendelseDao = PostgresHendelseDao(dataSource)

    @Test
    fun `lagrer en hendelse i hendelsestabellen`() {
        val id = UUID.randomUUID()
        val hendelse = Testhendelse(id)
        assertEquals(0, antallRader(id))
        assertTrue(hendelseDao.lagre(hendelse))
        assertEquals(1, antallRader(id))
        assertFalse(hendelseDao.lagre(hendelse))
        assertEquals(1, antallRader(id))
        val (opprettet, type, data) = hent(id)
        assertEquals(LocalDate.EPOCH.atStartOfDay(), opprettet)
        assertEquals("TULLETYPE", type)
        assertEquals("""{"test": true}""", data)
    }


    @BeforeEach
    fun beforeEach() {
        sessionOf(dataSource).use { session ->
            session.run(queryOf("truncate table hendelse cascade;").asExecute)
        }
    }

    private fun antallRader(id: UUID) = sessionOf(dataSource).use { session ->
        session.run(queryOf("select count(1) from hendelse where id='$id'").map { row -> row.int(1) }.asSingle)
    } ?: 0

    private fun hent(id: UUID) = sessionOf(dataSource, strict = true).use { session ->
        session.run(queryOf("select * from hendelse where id='$id'").map { row -> Triple(row.localDateTime("opprettet"), row.string("type"), row.string("data")) }.asSingle)
    } ?: throw IllegalStateException("Fant ikke hendelse med id $id")
}