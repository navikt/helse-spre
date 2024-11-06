package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.test_support.TestDataSource
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.databaseContainer
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Hendelse
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.PostgresHendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Testhendelse
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

internal abstract class BehandlingshendelseJsonMigreringTest(
    private val migrering: BehandlingshendelseJsonMigrering,
    private val forrigeVersjon: MigrationVersion
) {
    internal constructor(migrering: BehandlingshendelseJsonMigrering): this(
        migrering = migrering,
        forrigeVersjon = migrering.forrigeVersjon
    )

    protected lateinit var dataSource: TestDataSource
    private lateinit var hendelseDao: PostgresHendelseDao

    private var hendelserFørMigrering = listOf<Behandlingshendelse>()
    private var nyeEllerEndredeHendelserEtterMigrering = mutableListOf<Behandlingshendelse>()

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
            .target(forrigeVersjon)
            .load().let {
                it.clean()
                it.migrate()
        }
    }

    @AfterEach
    fun afterEach() {
        databaseContainer.droppTilkobling(dataSource)
        assertEquals(emptyList<Behandlingshendelse>(), nyeEllerEndredeHendelserEtterMigrering)
    }

    protected fun migrer() {
        val raderFør = antallRader()
        hendelserFørMigrering = alleBehandlingshendelser()

        Flyway.configure().dataSource(dataSource.ds).javaMigrations(migrering).target(migrering.version).load().migrate()
        val raderEtter = antallRader()
        val hendelserEtter = alleBehandlingshendelser()

        nyeEllerEndredeHendelserEtterMigrering = (hendelserEtter - hendelserFørMigrering.toSet()).toMutableList()

        val antallNyeRader = nyeEllerEndredeHendelserEtterMigrering.size / 2 // En med oppdaterte flagg + en ny rad som korrigerer
        assertEquals(raderEtter - raderFør, antallNyeRader)
    }

    private fun finnGammelOgNyHendelse(rad: Rad): Pair<Behandlingshendelse, Behandlingshendelse> {
        val gammel = nyeEllerEndredeHendelserEtterMigrering.single { it.sekvensnummer == rad.sekvensnummer }
        val ny = nyeEllerEndredeHendelserEtterMigrering.single { it.sekvensnummer != rad.sekvensnummer && it.behandlingId == gammel.behandlingId && it.funksjonellTid == gammel.funksjonellTid }
        // Fjerner dem slik at vi afterEach kan sjekke at lista er tom (det betyr at man _må_ asserte på alle rader som korrigeres.)
        assertTrue(nyeEllerEndredeHendelserEtterMigrering.remove(gammel))
        assertTrue(nyeEllerEndredeHendelserEtterMigrering.remove(ny))
        return gammel to ny
    }

    protected fun assertKorrigerte(vararg rader: Rad) = rader.forEach { assertKorrigert(it) }
    protected fun assertKorrigert(rad: Rad, assertion: (gammel: ObjectNode, ny: ObjectNode) -> Unit = { _,_ -> } ) {
        val (gammel, ny) = finnGammelOgNyHendelse(rad)

        // Den gamle raden skal være flagget som korrigert og er aldri siste
        assertTrue(gammel.erKorrigert)
        assertFalse(gammel.siste)

        val gammelFørMigrering = hendelserFørMigrering.single { it.sekvensnummer == gammel.sekvensnummer}
        // Om den gamle raden var markert med siste=true skal den nye nå være siste=true
        if (gammelFørMigrering.siste) assertTrue(ny.siste)
        else assertFalse(ny.siste)

        // Den nye raden skal ikke være markert som korrigert, og må ha en nyere teknisk tid enn den korrigerte raden
        assertFalse(ny.erKorrigert)
        assertTrue(ny.tekniskTid > gammel.tekniskTid)

        // Sjekker at versjonen på den nye raden er korrekt
        if (migrering.nyVersjon() == null) {
            assertEquals(gammel.versjon, ny.versjon)
        } else {
            assertEquals(migrering.nyVersjon(), Versjon.of(ny.versjon))
        }

        // Egne assertions for data
        assertion(gammel.data, ny.data)
    }

    protected fun leggTilBehandlingshendelse(
        sakId: UUID = UUID.randomUUID(),
        behandlingId: UUID,
        siste: Boolean = true,
        versjon: Versjon = Versjon.of("1.0.0"),
        erKorrigert: Boolean = false,
        funksjonellTid: LocalDateTime = LocalDateTime.now(),
        hendelse: Hendelse = Testhendelse(UUID.randomUUID()),
        data: (data: ObjectNode) -> ObjectNode = { it }
    ): Rad {
        hendelseDao.lagre(hendelse)

        sessionOf(dataSource.ds, returnGeneratedKey = true).use { session ->
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
                "hendelseId" to hendelse.id,
                "erKorrigert" to erKorrigert
            )).asUpdateAndReturnGeneratedKey)!!
            return Rad(sekvensnummer)
        }
    }

    private fun alleBehandlingshendelser() = sessionOf(dataSource.ds).use { session ->
        session.run(queryOf("select * from behandlingshendelse order by tekniskTid").map { row -> Behandlingshendelse(row) }.asList)
    }
    private fun antallRader() = sessionOf(dataSource.ds).use { session ->
        session.run(queryOf("select count(1) from behandlingshendelse").map { row -> row.int(1) }.asSingle)
    } ?: 0

    private companion object {
        private val BehandlingshendelseJsonMigrering.forrigeVersjon get() = (this::class.simpleName ?: "").substringBefore("__").substringAfter("V").toInt().let { MigrationVersion.fromVersion("${it - 1}") }
        private val objectMapper = jacksonObjectMapper()
        private data class Behandlingshendelse(
            val sekvensnummer: Long,
            val behandlingId: UUID,
            val funksjonellTid: OffsetDateTime,
            val tekniskTid: OffsetDateTime,
            val siste: Boolean,
            val versjon: String,
            val erKorrigert: Boolean,
            val data: ObjectNode
        ) {
            constructor(row: Row): this(
                sekvensnummer = row.long("sekvensnummer"),
                behandlingId = row.uuid("behandlingId"),
                funksjonellTid = row.offsetDateTime("funksjonellTid"),
                tekniskTid = row.offsetDateTime("tekniskTid"),
                siste = row.boolean("siste"),
                versjon = row.string("versjon"),
                erKorrigert = row.boolean("er_korrigert"),
                data = objectMapper.readTree(row.string("data")) as ObjectNode
            )
        }
    }
}

internal class Rad(val sekvensnummer: Long)
