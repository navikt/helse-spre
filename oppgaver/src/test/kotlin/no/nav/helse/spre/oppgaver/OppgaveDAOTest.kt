package no.nav.helse.spre.oppgaver

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class OppgaveDAOTest {
    private lateinit var embeddedPostgres: EmbeddedPostgres
    private lateinit var hikariConfig: HikariConfig
    private lateinit var dataSource: HikariDataSource
    private lateinit var oppgaveDAO: OppgaveDAO

    @BeforeAll
    fun setup() {
        embeddedPostgres = EmbeddedPostgres.builder().start()

        hikariConfig = HikariConfig().apply {
            this.jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres")
            maximumPoolSize = 3
            minimumIdle = 1
            idleTimeout = 10001
            connectionTimeout = 1000
            maxLifetime = 30001
        }

        dataSource = HikariDataSource(hikariConfig)

        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()

        oppgaveDAO = OppgaveDAO(dataSource)
    }

    @Test
    fun `finner ikke en ikke-eksisterende oppgave`() {
        assertNull(oppgaveDAO.finnOppgave(hendelseId = UUID.randomUUID()))
    }

    @Test
    fun `finner en eksisterende oppgave`() {
        val hendelseId = UUID.randomUUID()
        val dokumentId = UUID.randomUUID()
        oppgaveDAO.opprettOppgaveHvisNy(
            hendelseId = hendelseId,
            dokumentId = dokumentId,
            dokumentType = DokumentType.Søknad
        )
        val oppgave = oppgaveDAO.finnOppgave(hendelseId)
        assertNotNull(oppgave)
        assertEquals(
            hendelseId = hendelseId,
            dokumentId = dokumentId,
            tilstand = Oppgave.Tilstand.DokumentOppdaget,
            dokumentType = DokumentType.Søknad,
            oppgave = requireNotNull(oppgave),
        )
        oppgaveDAO.oppdaterTilstand(oppgave)
        assertTrue(oppgaveDAO.finnOppgave(hendelseId)!!.sistEndret!!.isAfter(oppgave.sistEndret))

    }

    private fun assertEquals(
        hendelseId: UUID,
        dokumentId: UUID,
        tilstand: Oppgave.Tilstand,
        dokumentType: DokumentType,
        oppgave: Oppgave
    ) {
        assertEquals(hendelseId, oppgave.hendelseId)
        assertEquals(dokumentId, oppgave.dokumentId)
        assertEquals(tilstand, oppgave.tilstand)
        assertEquals(dokumentType, oppgave.dokumentType)
        assertTrue(ChronoUnit.SECONDS.between(LocalDateTime.now(), oppgave.sistEndret) < 5)
    }
}
