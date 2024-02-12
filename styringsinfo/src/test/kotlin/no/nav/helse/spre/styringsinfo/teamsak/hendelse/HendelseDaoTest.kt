package no.nav.helse.spre.styringsinfo.teamsak.hendelse


import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest
import no.nav.helse.spre.styringsinfo.teamsak.PostgresHendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class HendelseDaoTest: AbstractDatabaseTest() {

    private val hendelseDao = PostgresHendelseDao(dataSource)

    @Test
    fun `lagrer en hendelse i hendelsestabellen`() {
        val id = UUID.randomUUID()
        val hendelse = Testhendelse(id)
        hendelseDao.lagre(hendelse)
        assertEquals(1, antallRader(id))
    }


    @BeforeEach
    fun beforeEach() {
        sessionOf(dataSource).use { session ->
            session.run(queryOf("truncate table hendelse cascade;").asExecute)
        }
    }

    private fun antallRader(id: UUID) =  sessionOf(dataSource).use { session ->
        session.run(queryOf("select count(1) from hendelse where id='$id'").map { row -> row.int(1) }.asSingle)
    } ?: 0

}

private class Testhendelse(override val id: UUID) : Hendelse {

    override val opprettet: LocalDateTime = LocalDate.EPOCH.atStartOfDay()
    override val type: String = "TULLETYPE"
    override val data: JsonNode = jacksonObjectMapper().createObjectNode()

    override fun håndter(hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        TODO("Not yet implemented")
    }

}