package no.nav.helse.spre.oppgaver

import com.github.navikt.tbd_libs.test_support.TestDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS
import java.util.*

internal class OppgaveDAOTest {
    private lateinit var dataSource: TestDataSource
    private lateinit var oppgaveDAO: OppgaveDAO
    private val observer = object : Oppgave.Observer {}

    @BeforeEach
    fun reset() {
        dataSource = databaseContainer.nyTilkobling()
        oppgaveDAO = OppgaveDAO(dataSource.ds)
    }

    @AfterEach
    fun after() {
        databaseContainer.droppTilkobling(dataSource)
    }

    @Test
    fun `finner ikke en ikke-eksisterende oppgave`() {
        assertNull(oppgaveDAO.finnOppgave(hendelseId = UUID.randomUUID(), observer))
    }

    @Test
    fun `finner en eksisterende oppgave`() {
        val hendelseId = UUID.randomUUID()
        val dokumentId = UUID.randomUUID()
        oppgaveDAO.opprettOppgaveHvisNy(
            hendelseId = hendelseId,
            dokumentId = dokumentId,
            fødselsnummer = "123",
            orgnummer = "456",
            dokumentType = DokumentType.Søknad
        )
        val oppgave = oppgaveDAO.finnOppgave(hendelseId, observer)
        assertNotNull(oppgave)
        assertEquals(
            hendelseId = hendelseId,
            dokumentId = dokumentId,
            fødselsnummer = "123",
            orgnummer = "456",
            tilstand = Oppgave.Tilstand.DokumentOppdaget,
            dokumentType = DokumentType.Søknad,
            oppgave = requireNotNull(oppgave),
        )
        oppgaveDAO.oppdaterTilstand(hendelseId, Oppgave.Tilstand.DokumentOppdaget)
        assertTrue(oppgaveDAO.finnOppgave(hendelseId, observer)!!.sistEndret!!.isAfter(oppgave.sistEndret))
    }

    @Test
    fun `hente opp timeout`() {
        val dokumentId = UUID.randomUUID()
        val timeout = LocalDateTime.now()
        oppgaveDAO.lagreTimeout(dokumentId, timeout)
        assertTidsstempel(timeout, oppgaveDAO.hentTimeout(dokumentId, timeout.minusSeconds(5)))
        assertTidsstempel(timeout, oppgaveDAO.hentTimeout(dokumentId, timeout))
        val foreslåttTimeout = timeout.plusSeconds(5)
        assertTidsstempel(foreslåttTimeout, oppgaveDAO.hentTimeout(dokumentId, foreslåttTimeout))

        val ikkeLagretDokumentId = UUID.randomUUID()
        assertTidsstempel(foreslåttTimeout, oppgaveDAO.hentTimeout(ikkeLagretDokumentId, foreslåttTimeout))
    }

    @Test
    fun `oppdatering av timeout setter kun timeout for tilhørende dokument`() {
        val dokumentId1 = UUID.randomUUID()
        val timeout1 = LocalDateTime.now()
        oppgaveDAO.lagreTimeout(dokumentId1, timeout1)
        val dokumentId2 = UUID.randomUUID()
        val timeout2 = timeout1.plusSeconds(5)
        oppgaveDAO.lagreTimeout(dokumentId2, timeout2)

        oppgaveDAO.lagreTimeout(dokumentId1, timeout1.plusSeconds(10))

        assertTidsstempel(timeout1.plusSeconds(10), oppgaveDAO.hentTimeout(dokumentId1, timeout1))
        assertTidsstempel(timeout2, oppgaveDAO.hentTimeout(dokumentId2, timeout1))
    }

    private fun assertTidsstempel(forventet: LocalDateTime, faktisk: LocalDateTime) = assertEquals(forventet.truncatedTo(SECONDS), faktisk.truncatedTo(SECONDS))
    private fun assertEquals(
        hendelseId: UUID,
        dokumentId: UUID,
        fødselsnummer: String,
        orgnummer: String,
        tilstand: Oppgave.Tilstand,
        dokumentType: DokumentType,
        oppgave: Oppgave
    ) {
        assertEquals(hendelseId, oppgave.hendelseId)
        assertEquals(dokumentId, oppgave.dokumentId)
        assertEquals(fødselsnummer, oppgave.fødselsnummer)
        assertEquals(orgnummer, oppgave.orgnummer)
        assertEquals(tilstand, oppgave.tilstand)
        assertEquals(dokumentType, oppgave.dokumentType)
        assertTrue(SECONDS.between(LocalDateTime.now(), oppgave.sistEndret) < 5)
    }
}
