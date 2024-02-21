package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.PostgresHendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Testhendelse
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal abstract class BehandlingshendelseJsonMigreringTest(
    private val migrering: BehandlingshendelseJsonMigrering,
    private val forrigeVersjon: MigrationVersion,
    private val dataSource: DataSource
) {
    internal constructor(migrering: BehandlingshendelseJsonMigrering, dataSource: DataSource): this(
        migrering = migrering,
        forrigeVersjon = migrering.forrigeVersjon,
        dataSource = dataSource
    )

    private val hendelseDao = PostgresHendelseDao(dataSource)
    private var nyeEllerEndretEtterMigrering: MutableList<Behandlingshendelse> = mutableListOf()

    @BeforeEach
    fun beforeEach() {
        val cleanupQuery = """
            drop publication if exists spre_styringsinfo_publication; 
            select pg_drop_replication_slot('spre_styringsinfo_replication');
        """
        kotlin.runCatching { sessionOf(dataSource).use { session ->
            session.run(queryOf(cleanupQuery).asExecute)
        }}
        Flyway.configure()
            .dataSource(dataSource)
            .cleanDisabled(false)
            .target(forrigeVersjon)
            .load().let {
                it.clean()
                it.migrate()
        }
    }

    @AfterEach
    fun afterEach() {
        assertEquals(emptyList<Behandlingshendelse>(), nyeEllerEndretEtterMigrering)
    }

    protected fun migrer() {
        val raderFør = antallRader()
        val hendelserFør = alleBehandlingshendelser()

        Flyway.configure().dataSource(dataSource).javaMigrations(migrering).target(migrering.version).load().migrate()
        val raderEtter = antallRader()
        val hendelserEtter = alleBehandlingshendelser()

        nyeEllerEndretEtterMigrering = (hendelserEtter - hendelserFør.toSet()).toMutableList()

        val antallNyeRader = nyeEllerEndretEtterMigrering.size / 2 // En med oppdaterte flagg + en ny rad som korrigerer
        assertEquals(raderEtter - raderFør, antallNyeRader)
    }

    private fun finnGammelOgNyHendelse(rad: Rad): Pair<Behandlingshendelse, Behandlingshendelse> {
        val gammel = nyeEllerEndretEtterMigrering.single { it.sekvensnummer == rad.sekvensnummer }
        val ny = nyeEllerEndretEtterMigrering.single { it.sekvensnummer != rad.sekvensnummer && it.behandlingId == gammel.behandlingId && it.funksjonellTid == gammel.funksjonellTid }
        assertTrue(nyeEllerEndretEtterMigrering.remove(gammel))
        assertTrue(nyeEllerEndretEtterMigrering.remove(ny))
        return gammel to ny
    }

    protected fun assertKorrigerte(vararg rader: Rad) = rader.forEach { assertKorrigert(it) }
    protected fun assertKorrigert(rad: Rad, assertion: (gammel: ObjectNode, ny: ObjectNode) -> Unit = { _,_ -> } ) {
        val (gammel, ny) = finnGammelOgNyHendelse(rad)

        assertTrue(gammel.erKorrigert)
        assertFalse(gammel.siste)

        // TODO: Dette mangler jo da..
        // Om den gamle raden var markert med siste=true skal den nye nå være siste=true
        // if (gammelFørMigrering.siste) assertTrue(ny.siste)
        //else assertFalse(ny.siste)

        // Den nye raden skal ikke være markert som korrigert, og må ha en nyere teknisk tid enn den korrigerte raden
        assertFalse(ny.erKorrigert)
        assertTrue(ny.tekniskTid > gammel.tekniskTid)

        // Sjekker at versjonen på den nye raden er korrekt
        migrering.nyVersjon()?.let { assertEquals(it, Versjon.of(ny.versjon)) }

        // Egne assertions for data
        assertion(gammel.data, ny.data)
    }

    protected fun leggTilRad(
        sakId: UUID = UUID.randomUUID(),
        behandlingId: UUID,
        siste: Boolean,
        versjon: Versjon,
        erKorrigert: Boolean = false,
        funksjonellTid: LocalDateTime = LocalDateTime.now(),
        data: (data: ObjectNode) -> ObjectNode = { it }
    ): Rad {
        val hendelseId = UUID.randomUUID()
        hendelseDao.lagre(Testhendelse(hendelseId))

        sessionOf(dataSource, returnGeneratedKey = true).use { session ->
            val sql = """
            insert into behandlingshendelse(sakId, behandlingId, funksjonellTid, versjon, data, siste, hendelseId, er_korrigert) 
            values(:sakId, :behandlingId, :funksjonellTid, :versjon, :data::jsonb, :siste, :hendelseId, :erKorrigert)
            """

            val sekvensnummer = session.run(queryOf(sql, mapOf(
                "sakId" to sakId,
                "behandlingId" to behandlingId,
                "funksjonellTid" to funksjonellTid,
                "versjon" to versjon.toString(),
                "siste" to siste,
                "data" to data(objectMapper.createObjectNode()).toString(),
                "hendelseId" to hendelseId,
                "erKorrigert" to erKorrigert
            )).asUpdateAndReturnGeneratedKey)!!
            return Rad(sekvensnummer)
        }
    }

    private fun alleBehandlingshendelser() = sessionOf(dataSource).use { session ->
        session.run(queryOf("select * from behandlingshendelse order by tekniskTid").map { row -> Behandlingshendelse(row) }.asList)
    }
    private fun antallRader() = sessionOf(dataSource).use { session ->
        session.run(queryOf("select count(1) from behandlingshendelse").map { row -> row.int(1) }.asSingle)
    } ?: 0

    private companion object {
        private val BehandlingshendelseJsonMigrering.forrigeVersjon get() = (this::class.simpleName ?: "").substringBefore("__").substringAfter("V").toInt().let { MigrationVersion.fromVersion("${it - 1}") }
        private val objectMapper = jacksonObjectMapper()
        private data class Behandlingshendelse(
            val sekvensnummer: Long,
            val behandlingId: UUID,
            val funksjonellTid: LocalDateTime,
            val tekniskTid: LocalDateTime,
            val siste: Boolean,
            val versjon: String,
            val erKorrigert: Boolean,
            val data: ObjectNode
        ) {
            constructor(row: Row): this(
                sekvensnummer = row.long("sekvensnummer"),
                behandlingId = row.uuid("behandlingId"),
                funksjonellTid = row.localDateTime("funksjonellTid"),
                tekniskTid = row.localDateTime("tekniskTid"),
                siste = row.boolean("siste"),
                versjon = row.string("versjon"),
                erKorrigert = row.boolean("er_korrigert"),
                data = objectMapper.readTree(row.string("data")) as ObjectNode
            )
        }
    }
}

internal class Rad(val sekvensnummer: Long)
