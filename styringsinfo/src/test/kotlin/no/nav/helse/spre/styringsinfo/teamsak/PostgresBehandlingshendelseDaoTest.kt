package no.nav.helse.spre.styringsinfo.teamsak

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest
import no.nav.helse.spre.styringsinfo.teamsak.behandling.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingskilde.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.Manuell
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.Henlagt
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.Registrert
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstype.Førstegangsbehandling
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import java.time.LocalDateTime
import java.util.UUID

@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
internal class PostgresBehandlingshendelseDaoTest: AbstractDatabaseTest() {

    private val behandlingshendelseDao: BehandlingshendelseDao = PostgresBehandlingshendelseDao(dataSource)

    @Test
    fun `korrigerer feilsendt opplysning på siste rad`() {
        val behandlingId = BehandlingId(UUID.randomUUID())
        assertEquals(0, behandlingId.rader)
        val behandling = nyBehandling(behandlingId)
        behandlingshendelseDao.lagre(behandling)
        assertEquals(1, behandlingId.rader)
        val korrigertKilde = behandling.copy(behandlingskilde = Saksbehandler)
        behandlingshendelseDao.lagre(korrigertKilde)
        assertEquals(2, behandlingId.rader)
        assertEquals(Saksbehandler, behandlingshendelseDao.hent(behandlingId)!!.behandlingskilde)
    }

    @Test
    fun `korrigerer feilsendt opplysning på tidligere rad`() {
        val behandlingId = BehandlingId(UUID.randomUUID())
        assertEquals(0, behandlingId.rader)
        val behandling = nyBehandling(behandlingId)
        behandlingshendelseDao.lagre(behandling)
        assertEquals(1, behandlingId.rader)
        val nyInfo = behandling.copy(behandlingsresultat = Henlagt, funksjonellTid = LocalDateTime.now())
        behandlingshendelseDao.lagre(nyInfo)
        assertEquals(2, behandlingId.rader)
        assertEquals(Henlagt, behandlingshendelseDao.hent(behandlingId)!!.behandlingsresultat)
        val korrigererFørste = behandling.copy(behandlingskilde = Arbeidsgiver)
        behandlingshendelseDao.lagre(korrigererFørste)
        assertEquals(3, behandlingId.rader)
        // Ettersom vi korrigerer en tidligere rad er det ikke det den siste vi bygger videre på for nye meldinger.
        assertEquals(Henlagt, behandlingshendelseDao.hent(behandlingId)!!.behandlingsresultat)
        assertEquals(System, behandlingshendelseDao.hent(behandlingId)!!.behandlingskilde)
    }

    @Test
    fun `lagrer ikke ny rad for funksjonelle like behandlinger`() {
        val behandlingId = BehandlingId(UUID.randomUUID())
        assertEquals(0, behandlingId.rader)
        val behandling = nyBehandling(behandlingId)
        behandlingshendelseDao.lagre(behandling)
        assertEquals(1, behandlingId.rader)
        behandlingshendelseDao.lagre(behandling)
        behandlingshendelseDao.lagre(behandling.copy(funksjonellTid = LocalDateTime.now()))
        assertEquals(1, behandlingId.rader)
    }

    @BeforeEach
    fun beforeEach() {
        sessionOf(dataSource).use { session ->
            session.run(queryOf("truncate table behandlingshendelse;").asExecute)
        }
    }

    private val BehandlingId.rader get() =  sessionOf(dataSource).use { session ->
        session.run(queryOf("select count(1) from behandlingshendelse where behandlingId='$this'").map { row -> row.int(1) }.asSingle)
    } ?: 0

    private fun nyBehandling(behandlingId: BehandlingId) = Behandling(
        sakId = SakId(UUID.randomUUID()),
        behandlingId = behandlingId,
        relatertBehandlingId = null,
        aktørId = "1",
        mottattTid = LocalDateTime.now(),
        registrertTid = LocalDateTime.now(),
        funksjonellTid = LocalDateTime.now(),
        behandlingstatus =  Registrert,
        behandlingskilde = System,
        behandlingstype = Førstegangsbehandling,
        behandlingsmetode = Manuell
    )
}