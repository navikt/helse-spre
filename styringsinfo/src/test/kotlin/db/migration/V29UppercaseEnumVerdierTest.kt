package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.PostgresBehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.PostgresHendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Testhendelse
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class V29UppercaseEnumVerdierTest {
    private val hendelseDao = PostgresHendelseDao(dataSource)
    private val behandlingshendelseDao = PostgresBehandlingshendelseDao(dataSource)
    private val flywayConfig = Flyway.configure()
        .dataSource(dataSource)
        .failOnMissingLocations(true)
        .cleanDisabled(false)

    @BeforeEach
    fun beforeEach() {
        kotlin.runCatching {
            sessionOf(dataSource).use { session ->
                session.run(queryOf("""
                drop publication if exists spre_styringsinfo_publication; 
                select pg_drop_replication_slot('spre_styringsinfo_replication');
            """).asExecute)
            }
        }
        flywayConfig.target(MigrationVersion.fromVersion("28")).load().let {
            it.clean()
            it.migrate()
        }
    }

    @Test
    fun `Legger til korrigerende rader for å rette opp i feil enum-verdier i versjon 0_0_1`() {
        val behandlingId1 = UUID.randomUUID()
        leggTilRad(behandlingId1, false, behandlingsresultat = null)
        val behandling1SisteSekvensnummer = leggTilRad(behandlingId1, true)
        assertEquals(2, antallRader(behandlingId1))

        val behandlingId2 = UUID.randomUUID()
        leggTilRad(behandlingId2, false)
        leggTilRad(behandlingId2, false)
        val behandling2SisteSekvensnummer = leggTilRad(behandlingId2, true)
        assertEquals(3, antallRader(behandlingId2))

        //assertThrows<IllegalArgumentException> { behandlingshendelseDao.hent(BehandlingId(behandlingId1)) }
        //assertThrows<IllegalArgumentException> { behandlingshendelseDao.hent(BehandlingId(behandlingId2)) }

        flywayConfig.target(MigrationVersion.fromVersion("29")).load().migrate()
        assertEquals(4, antallRader(behandlingId1))
        assertEquals(6, antallRader(behandlingId2))

        assertDoesNotThrow { behandlingshendelseDao.hent(BehandlingId(behandlingId1)) }
        assertDoesNotThrow { behandlingshendelseDao.hent(BehandlingId(behandlingId2)) }

        assertRader(behandlingId1, behandling1SisteSekvensnummer)
        assertRader(behandlingId2, behandling2SisteSekvensnummer)
    }

    @Test
    fun `Migrerer kun behandlingshendelser med versjon 0_0_1`() {
        val behandlingId = UUID.randomUUID()
        leggTilRad(behandlingId, false, "0.0.2")
        assertEquals(1, antallRader(behandlingId))
        flywayConfig.target(MigrationVersion.fromVersion("29")).load().migrate()
        assertEquals(1, antallRader(behandlingId))
    }

    @Test
    fun `Skal ikke legge til korrigerende rader for rader som allerede er korrigert`() {
        val behandlingId = UUID.randomUUID()
        val funksjonellTid = LocalDateTime.now()
        leggTilRad(behandlingId, false, "0.0.1", erKorrigert = true, funksjonellTid = funksjonellTid)
        val rad2Sekvensnummer = leggTilRad(behandlingId, true, "0.0.1", erKorrigert = false, funksjonellTid = funksjonellTid)

        assertEquals(2, antallRader(behandlingId))
        flywayConfig.target(MigrationVersion.fromVersion("29")).load().migrate()
        assertEquals(3, antallRader(behandlingId))

        val behandlingshendelser = alleBehandlingshendelser(behandlingId)
        assertTrue(behandlingshendelser[0].erKorrigert)
        assertTrue(behandlingshendelser[1].erKorrigert)
        assertFalse(behandlingshendelser[2].erKorrigert)
    }

    private fun leggTilRad(behandlingId: UUID, siste: Boolean, versjon: String = "0.0.1", erKorrigert: Boolean = false, funksjonellTid: LocalDateTime = LocalDateTime.now(), behandlingsresultat: String? = "Vedtatt"): Long {
        val hendelseId = UUID.randomUUID()
        hendelseDao.lagre(Testhendelse(hendelseId))
        sessionOf(dataSource, returnGeneratedKey = true).use { session ->
            val sql = """
            insert into behandlingshendelse(sakId, behandlingId, funksjonellTid, versjon, data, siste, hendelseId, er_korrigert) 
            values(:sakId, :behandlingId, :funksjonellTid, :versjon, :data::jsonb, :siste, :hendelseId, :erKorrigert)
        """

            val epoch = LocalDate.EPOCH.atStartOfDay()
            val data = objectMapper.createObjectNode().apply {
                put("aktørId", "1234")
                put("mottattTid", "$epoch")
                put("registrertTid", "$epoch")
                put("behandlingstatus", "AvventerGodkjenning")
                put("behandlingtype", "Omgjøring")
                put("behandlingskilde", "Saksbehandler")
                putString("behandlingsmetode", "Automatisk")
                putString("relatertBehandlingId", null)
                putString("behandlingsresultat", behandlingsresultat)
            }

            return session.run(queryOf(sql, mapOf(
                "sakId" to UUID.randomUUID(),
                "behandlingId" to behandlingId,
                "funksjonellTid" to funksjonellTid,
                "versjon" to versjon,
                "siste" to siste,
                "data" to data.toString(),
                "hendelseId" to hendelseId,
                "erKorrigert" to erKorrigert
            )).asUpdateAndReturnGeneratedKey)!!
        }
    }

    private fun antallRader(behandlingId: UUID) = sessionOf(dataSource).use { session ->
        session.run(queryOf("select count(1) from behandlingshendelse where behandlingId='$behandlingId'").map { row -> row.int(1) }.asSingle)
    } ?: 0

    private fun assertRader(behandlingId: UUID, sisteSekvensnummerFørMigrering: Long) {
        val alleBehandlingshendelser = alleBehandlingshendelser(behandlingId)
        alleBehandlingshendelser.groupBy { it.funksjonellTid }.forEach { (_, behandlingshendelser) ->
            assertEquals(2, behandlingshendelser.size)
            val gammel = behandlingshendelser[0]
            val ny = behandlingshendelser[1]

            assertTrue(gammel.erKorrigert)
            assertFalse(ny.erKorrigert)
            assertTrue(ny.tekniskTid > gammel.tekniskTid)
            assertEquals("Automatisk", gammel.behandlingsmetode)
            assertEquals("AUTOMATISK", ny.behandlingsmetode)
            assertEquals("AvventerGodkjenning", gammel.behandlingstatus)
            assertEquals("AVVENTER_GODKJENNING", ny.behandlingstatus)
            assertEquals("Saksbehandler", gammel.behandlingskilde)
            assertEquals("SAKSBEHANDLER", ny.behandlingskilde)
            assertEquals("Omgjøring", gammel.behandlingstype)
            assertEquals("OMGJØRING", ny.behandlingstype)
            if(gammel.behandlingsresultat == null) {
                assertNull(ny.behandlingsresultat)
            } else {
                assertEquals("Vedtatt", gammel.behandlingsresultat)
                assertEquals("VEDTATT", ny.behandlingsresultat)
            }
            assertEquals("0.0.1", gammel.versjon)
            assertEquals("0.0.2", ny.versjon)
            assertFalse(gammel.siste)

            if (gammel.sekvensnummer == sisteSekvensnummerFørMigrering) assertTrue(ny.siste)
            else assertFalse(ny.siste)
        }
    }

    private fun alleBehandlingshendelser(behandlingId: UUID) = sessionOf(dataSource).use { session -> session.run(queryOf("select * from behandlingshendelse where behandlingId='$behandlingId' order by tekniskTid").map { row ->
        val data = objectMapper.readTree(row.string("data"))
        SpennendeFelter(
            sekvensnummer = row.long("sekvensnummer"),
            funksjonellTid = row.localDateTime("funksjonellTid"),
            tekniskTid = row.localDateTime("tekniskTid"),
            siste = row.boolean("siste"),
            versjon = row.string("versjon"),
            erKorrigert = row.boolean("er_korrigert"),
            // Felter som ligger inne i "data"-bloben
            behandlingsmetode = data.path("behandlingsmetode").asText(),
            behandlingstype = data.path("behandlingtype").asText(), // TODO: Deilig at vi har lagret det som "behandlingtype" (uten s) - det kan vi vurdere å migrere
            behandlingsresultat = data.path("behandlingsresultat").takeUnless { it.isMissingOrNull() }?.asText(),
            behandlingstatus = data.path("behandlingstatus").asText(),
            behandlingskilde = data.path("behandlingskilde").asText()
        )
    }.asList)}

    private data class SpennendeFelter(
        val sekvensnummer: Long,
        val behandlingsmetode: String,
        val behandlingstype: String,
        val behandlingsresultat: String?,
        val behandlingstatus: String,
        val behandlingskilde: String,
        val funksjonellTid: LocalDateTime,
        val tekniskTid: LocalDateTime,
        val siste: Boolean,
        val versjon: String,
        val erKorrigert: Boolean
    )

    private val objectMapper = jacksonObjectMapper()
    private fun ObjectNode.putString(fieldName: String, value: String?) {
        if (value == null) putNull(fieldName)
        else put(fieldName, value)
    }
}