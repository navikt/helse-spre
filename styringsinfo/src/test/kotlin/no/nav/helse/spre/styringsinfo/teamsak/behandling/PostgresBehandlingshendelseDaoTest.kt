package no.nav.helse.spre.styringsinfo.teamsak.behandling

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingskilde.SAKSBEHANDLER
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingskilde.SYSTEM
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.MANUELL
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.REGISTRERT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstype.SØKNAD
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.PostgresHendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Testhendelse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.IllegalStateException
import java.time.OffsetDateTime
import java.util.UUID

internal class PostgresBehandlingshendelseDaoTest: AbstractDatabaseTest() {

    private val hendelseId = UUID.randomUUID()
    private val testehendelse = Testhendelse(hendelseId)

    private lateinit var behandlingshendelseDao: BehandlingshendelseDao
    private lateinit var hendelseDao: HendelseDao

    private val før = OffsetDateTime.parse("2024-03-01T13:52:53.123455+01:00")
    private val nå = OffsetDateTime.parse("2024-03-01T13:52:53.123456+01:00")
    private val etter = OffsetDateTime.parse("2024-03-01T13:52:53.123457+01:00")

    @BeforeEach
    fun setup() {
        behandlingshendelseDao = PostgresBehandlingshendelseDao(testDataSource.ds)
        hendelseDao = PostgresHendelseDao(testDataSource.ds)
    }

    @Test
    fun `kaster exception dersom behandling ikke finnes`() {
        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            behandlingshendelseDao.initialiser(BehandlingId(UUID.randomUUID()))
        }
    }

    @Test
    fun `lagrer ikke ny rad som har lik funksjonell tid, selv om behandlingen har annen info (korringering)`() {
        val behandlingId = BehandlingId(UUID.randomUUID())
        assertEquals(0, behandlingId.rader)
        val behandling = nyBehandling(behandlingId, nå, yrkesaktivitetstype = "ARBEIDSTAKER")
        assertTrue(behandlingshendelseDao.lagre(behandling, hendelseId))
        assertEquals(1, behandlingId.rader)
        val korrigertInfo = behandling.copy(behandlingskilde = SAKSBEHANDLER)
        assertFalse(behandlingshendelseDao.lagre(korrigertInfo, hendelseId))
        assertEquals(1, behandlingId.rader)
    }

    @Test
    fun `lagrer ikke ny rad som har tidligere funksjonell tid, selv om behandlingen har annen info (out-of-order)`() {
        val behandlingId = BehandlingId(UUID.randomUUID())
        assertEquals(0, behandlingId.rader)
        val behandling = nyBehandling(behandlingId, nå, yrkesaktivitetstype = "ARBEIDSTAKER")
        assertTrue(behandlingshendelseDao.lagre(behandling, hendelseId))
        assertEquals(1, behandlingId.rader)
        val korrigertInfo = behandling.copy(behandlingskilde = SAKSBEHANDLER, funksjonellTid = før)
        assertFalse(behandlingshendelseDao.lagre(korrigertInfo, hendelseId))
        assertEquals(1, behandlingId.rader)
    }

    @Test
    fun `lagrer ny rad som har nyere funksjonell tid med funksjonell endring`() {
        val behandlingId = BehandlingId(UUID.randomUUID())
        assertEquals(0, behandlingId.rader)
        val behandling = nyBehandling(behandlingId, nå, yrkesaktivitetstype = "ARBEIDSTAKER")
        assertTrue(behandlingshendelseDao.lagre(behandling, hendelseId))
        assertEquals(1, behandlingId.rader)
        val korrigertInfo = behandling.copy(behandlingskilde = SAKSBEHANDLER, funksjonellTid = etter)
        assertTrue(behandlingshendelseDao.lagre(korrigertInfo, hendelseId))
        assertEquals(2, behandlingId.rader)
    }

    @BeforeEach
    fun beforeEach() {
        hendelseDao.lagre(testehendelse)
    }

    private val BehandlingId.rader get() = sessionOf(testDataSource.ds).use { session ->
        session.run(queryOf("select count(1) from behandlingshendelse where behandlingId='$this'").map { row -> row.int(1) }.asSingle)
    } ?: 0

    private fun nyBehandling(behandlingId: BehandlingId, funksjonellTid: OffsetDateTime, behandlingsmetode: Behandling.Metode = MANUELL, yrkesaktivitetstype: String) = Behandling(
        sakId = SakId(UUID.randomUUID()),
        behandlingId = behandlingId,
        relatertBehandlingId = null,
        aktørId = "1",
        mottattTid = OffsetDateTime.parse("1970-01-01T00:00+01:00"),
        registrertTid = OffsetDateTime.parse("1970-01-01T00:00+01:00"),
        funksjonellTid = funksjonellTid,
        behandlingstatus = REGISTRERT,
        behandlingstype = SØKNAD,
        behandlingskilde = SYSTEM,
        behandlingsmetode = behandlingsmetode,
        saksbehandlerEnhet = "4488",
        hendelsesmetode = AUTOMATISK,
        yrkesaktivitetstype = yrkesaktivitetstype
    )
}
