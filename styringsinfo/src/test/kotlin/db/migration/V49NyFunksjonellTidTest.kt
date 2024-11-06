package db.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.test_support.TestDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.databaseContainer
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Hendelse
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.PostgresHendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Testhendelse
import no.nav.helse.spre.testhelpers.januar
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.*
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoUnit
import java.util.*

class V49NyFunksjonellTidTest {

    private lateinit var dataSource: TestDataSource
    private lateinit var hendelseDao: PostgresHendelseDao
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun beforeEach() {
        dataSource = databaseContainer.nyTilkobling()
        hendelseDao = PostgresHendelseDao(dataSource.ds)

        val cleanupQuery = """
            drop publication if exists spre_styringsinfo_publication; 
            select pg_drop_replication_slot('spre_styringsinfo_replication');
        """
        kotlin.runCatching { sessionOf(dataSource.ds).use { session ->
            session.run(queryOf(cleanupQuery).asExecute)
        }}
        Flyway.configure()
            .dataSource(dataSource.ds)
            .cleanDisabled(false)
            .target(MigrationVersion.fromVersion("48"))
            .load().let {
                it.clean()
                it.migrate()
            }
    }
    @AfterEach
    fun tearDown() {
        databaseContainer.droppTilkobling(dataSource)
    }

    @Test
    fun `kan dette virke, da?`() {

        //lage noen behandlinger
        val førsteJanuar = 1.januar.atTime(10, 10, 30, 102010203).atZone(ZoneId.systemDefault())
        val andreJanuar = 2.januar.atTime(20, 20, 30, 102010203).atZone(ZoneId.systemDefault())
        val tredjeJanuar = 3.januar.atTime(10, 10, 30, 102010203).atZone(ZoneId.systemDefault())
        val fjerdeJanuar = 4.januar.atTime(20, 20, 30, 102010203).atZone(ZoneId.systemDefault())

        val skalEndres = leggTilBehandlingshendelse(
            funksjonellTid = førsteJanuar,
            registrertTid = andreJanuar,
            hendelse = PågåendeBehandling(UUID.randomUUID())
        )

        val skalIkkeEndres = leggTilBehandlingshendelse(
            funksjonellTid = tredjeJanuar,
            registrertTid = fjerdeJanuar,
            hendelse = Testhendelse(UUID.randomUUID())
        )

        // kjøre migrering
        Flyway.configure().dataSource(dataSource.ds).target("49").load().migrate()

        // se at registrert tid og funksjonell tid er lik på de radene de skal være like på, men ikke på de andre.
        val henteSQL1 = """select funksjonellTid from behandlingshendelse where sekvensnummer = $skalEndres"""
        val henteSQL2 = """select funksjonellTid from behandlingshendelse where sekvensnummer = $skalIkkeEndres"""

        sessionOf(dataSource.ds).use { session ->
            val skulleVærtEndra= session.run(queryOf(henteSQL1).map { it.zonedDateTime("funksjonellTid") }.asSingle)!!
            assertTidssonerMedBareMikrosekunder(andreJanuar, skulleVærtEndra, "skulle skrive om; gjorde det ikke")
            val skulleIkkeVærtEndra= session.run(queryOf(henteSQL2).map { it.zonedDateTime("funksjonellTid") }.asSingle)!!
            assertTidssonerMedBareMikrosekunder(tredjeJanuar, skulleIkkeVærtEndra, "skulle ikke skrive om; gjorde det")
        }
    }

    private fun assertTidssonerMedBareMikrosekunder(expected: ZonedDateTime, actual: ZonedDateTime, msg: String) {
        assertEquals(expected.truncatedTo(ChronoUnit.MICROS), actual, msg)
    }

    private val tidsformatør = DateTimeFormatterBuilder().appendPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS").appendOffsetId().toFormatter()

    private fun leggTilBehandlingshendelse(
        funksjonellTid: ZonedDateTime = ZonedDateTime.now(),
        hendelse: Hendelse,
        registrertTid: ZonedDateTime
    ): Long {
        hendelseDao.lagre(hendelse)

        sessionOf(dataSource.ds, returnGeneratedKey = true).use { session ->
            val sql = """
            insert into behandlingshendelse(sakId, behandlingId, funksjonellTid, versjon, data, siste, hendelseId, er_korrigert) 
            values(:sakId, :behandlingId, :funksjonellTid, :versjon, :data::jsonb, :siste, :hendelseId, :erKorrigert)
            """

            val sekvensnummer = session.run(queryOf(sql, mapOf(
                "sakId" to UUID.randomUUID(),
                "behandlingId" to UUID.randomUUID(),
                "funksjonellTid" to funksjonellTid,
                "versjon" to "1.0.0",
                "siste" to true,
                "data" to objectMapper.readTree("""
                    {
                    "registrertTid": "${registrertTid.format(tidsformatør)}"
                    }
                """.trimIndent()).toString(),
                "hendelseId" to hendelse.id,
                "erKorrigert" to false
            )).asUpdateAndReturnGeneratedKey)!!
            return sekvensnummer
        }
    }

}

private class PågåendeBehandling(override val id: UUID) : Hendelse {
    override val opprettet: OffsetDateTime = OffsetDateTime.parse("1970-01-01T00:00+01:00")
    override val type: String = "pågående_behandlinger"
    override val data: JsonNode = jacksonObjectMapper().createObjectNode().apply { put("test", true) }
    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao) = throw IllegalStateException("Testehendelse skal ikke håndteres")
}