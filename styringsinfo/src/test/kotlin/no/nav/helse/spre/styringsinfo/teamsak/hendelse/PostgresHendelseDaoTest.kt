package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class PostgresHendelseDaoTest: AbstractDatabaseTest() {

    private lateinit var hendelseDao: PostgresHendelseDao

    @Test
    fun `lagrer en hendelse i hendelsestabellen`() {
        val id = UUID.randomUUID()
        val hendelse = Testhendelse(id)
        assertEquals(0, antallRader(id))
        hendelseDao.lagre(hendelse)
        assertEquals(1, antallRader(id))
        hendelseDao.lagre(hendelse)
        assertEquals(1, antallRader(id))
        val (type, data) = hent(id)
        assertEquals("TULLETYPE", type)
        assertEquals("""{"test": true}""", data)
    }


    @BeforeEach
    fun beforeEach() {
        hendelseDao = PostgresHendelseDao(testDataSource.ds)
    }

    private fun antallRader(id: UUID) = sessionOf(testDataSource.ds).use { session ->
        session.run(queryOf("select count(1) from hendelse where id='$id'").map { row -> row.int(1) }.asSingle)
    } ?: 0

    private fun hent(id: UUID) = sessionOf(testDataSource.ds, strict = true).use { session ->
        session.run(queryOf("select type, data from hendelse where id='$id'").map { row -> row.string("type") to row.string("data") }.asSingle)
    } ?: throw IllegalStateException("Fant ikke hendelse med id $id")
}