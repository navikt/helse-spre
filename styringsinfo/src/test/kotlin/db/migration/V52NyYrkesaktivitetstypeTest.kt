package db.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.test_support.TestDataSource
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatterBuilder
import java.util.*
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.databaseContainer
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.PostgresBehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Hendelse
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.PostgresHendelseDao
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class V52NyYrkesaktivitetstypeTest {

    private lateinit var dataSource: TestDataSource
    private lateinit var hendelseDao: PostgresHendelseDao
    private lateinit var behandlingDao: PostgresBehandlingshendelseDao
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun beforeEach() {
        dataSource = databaseContainer.nyTilkobling()
        hendelseDao = PostgresHendelseDao(dataSource.ds)
        behandlingDao = PostgresBehandlingshendelseDao(dataSource.ds)

        val cleanupQuery = """
            drop publication if exists spre_styringsinfo_publication; 
            select pg_drop_replication_slot('spre_styringsinfo_replication');
        """
        runCatching {
            sessionOf(dataSource.ds).use { session ->
                session.run(queryOf(cleanupQuery).asExecute)
            }
        }
        Flyway.configure()
            .dataSource(dataSource.ds)
            .cleanDisabled(false)
            .target(MigrationVersion.fromVersion("52"))
            .load().let {
                it.clean()
                it.migrate()
            }
    }

    @AfterEach
    fun tearDown() {
        databaseContainer.droppTilkobling(dataSource)
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
            insert into behandlingshendelse(sakId, behandlingId, yrkesaktivitetstype, funksjonellTid, versjon, data, siste, hendelseId, er_korrigert) 
            values(:sakId, :behandlingId, :yrkesaktivitetstype, :funksjonellTid, :versjon, :data::jsonb, :siste, :hendelseId, :erKorrigert)
            """

            val sekvensnummer = session.run(
                queryOf(
                    sql, mapOf(
                    "sakId" to UUID.randomUUID(),
                    "behandlingId" to UUID.randomUUID(),
                    "yrkesaktivitetstype" to hendelse.yrkesaktivitetstype,
                    "funksjonellTid" to funksjonellTid,
                    "versjon" to "1.0.0",
                    "siste" to true,
                    "data" to objectMapper.readTree(
                        """
                    {
                    "registrertTid": "${registrertTid.format(tidsformatør)}",
                    "mottattTid": "${ZonedDateTime.now().format(tidsformatør)}"
                    }
                """.trimIndent()
                    ).toString(),
                    "hendelseId" to hendelse.id,
                    "erKorrigert" to false
                )
                ).asUpdateAndReturnGeneratedKey
            )!!
            return sekvensnummer
        }
    }

    private fun leggTilBehandlingshendelseDefaulteYrkesaktivitetstype(
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

            val sekvensnummer = session.run(
                queryOf(
                    sql, mapOf(
                    "sakId" to UUID.randomUUID(),
                    "behandlingId" to UUID.randomUUID(),
                    "funksjonellTid" to funksjonellTid,
                    "versjon" to "1.0.0",
                    "siste" to true,
                    "data" to objectMapper.readTree(
                        """
                    {
                    "registrertTid": "${registrertTid.format(tidsformatør)}",
                    "mottattTid": "${ZonedDateTime.now().format(tidsformatør)}"
                    }
                """.trimIndent()
                    ).toString(),
                    "hendelseId" to hendelse.id,
                    "erKorrigert" to false
                )
                ).asUpdateAndReturnGeneratedKey
            )!!
            return sekvensnummer
        }
    }

    @Test
    fun `sjekker at vi defaulter til arbeidstaker`() {
        val hendelse = PågåendeBehandling(UUID.randomUUID(), "ARBEIDSTAKER")

        val id = leggTilBehandlingshendelseDefaulteYrkesaktivitetstype(
            registrertTid = ZonedDateTime.now(),
            hendelse = hendelse
        )

        val henteSQL = """select yrkesaktivitetstype from behandlingshendelse where sekvensnummer = $id"""

        sessionOf(dataSource.ds).use { session ->
            val yrkesaktivitetstype = session.run(queryOf(henteSQL).map { row -> row.string("yrkesaktivitetstype") }.asSingle)!!
            assertEquals("ARBEIDSTAKER", yrkesaktivitetstype)
        }
    }

    @Test
    fun `sjekker at vi legger til selvstendig`() {
        val hendelse = PågåendeBehandling(UUID.randomUUID(), "SELVSTENDIG")

        val id = leggTilBehandlingshendelse(
            registrertTid = ZonedDateTime.now(),
            hendelse = hendelse
        )

        val henteSQL = """select yrkesaktivitetstype from behandlingshendelse where sekvensnummer = $id"""

        sessionOf(dataSource.ds).use { session ->
            val yrkesaktivitetstype = session.run(queryOf(henteSQL).map { row -> row.string("yrkesaktivitetstype") }.asSingle)!!
            assertEquals("SELVSTENDIG", yrkesaktivitetstype)
        }
    }
}

private class PågåendeBehandling(override val id: UUID, yrkesaktivtetstype: String) : Hendelse {
    override val opprettet: OffsetDateTime = OffsetDateTime.parse("1970-01-01T00:00+01:00")
    override val type: String = "pågående_behandlinger"
    override val yrkesaktivitetstype: String = yrkesaktivtetstype
    override val data: JsonNode = jacksonObjectMapper().createObjectNode().apply { put("test", true) }
    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao) = throw IllegalStateException("Testehendelse skal ikke håndteres")
}
