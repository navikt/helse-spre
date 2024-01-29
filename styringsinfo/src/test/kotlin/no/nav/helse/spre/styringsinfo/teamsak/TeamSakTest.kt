package no.nav.helse.spre.styringsinfo.teamsak

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.AvsluttetUtenVedtak
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.GenerasjonOpprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.AvsluttetMedVedtak
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.GenerasjonForkastet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class TeamSakTest {

    private val behandlingDao: BehandlingDao = InMemoryBehandlingDao()

    @Test
    fun `start og slutt for vedtak`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val generasjonId = UUID.randomUUID()
        val aktørId = "1234"
        val innsendt = LocalDateTime.now()
        val registrert = innsendt.plusDays(1)

        val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), LocalDateTime.now(),
            blob, vedtaksperiodeId, generasjonId, aktørId, innsendt, registrert)

        val avsluttetMedVedtak = AvsluttetMedVedtak(UUID.randomUUID(), LocalDateTime.now(), blob, generasjonId)

        val behandlingId = generasjonId.behandlingId

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.KomplettFraBruker, behandling.behandlingStatus)

        avsluttetMedVedtak.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.AvsluttetMedVedtak, behandling.behandlingStatus)
    }

    @Test
    fun `start og slutt for auu`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val generasjonId = UUID.randomUUID()
        val aktørId = "1234"
        val innsendt = LocalDateTime.now()
        val registrert = innsendt.plusDays(1)

        val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), LocalDateTime.now(),
            blob, vedtaksperiodeId, generasjonId, aktørId, innsendt, registrert)

        val avsluttetUtenVedtak = AvsluttetUtenVedtak(UUID.randomUUID(), LocalDateTime.now(), blob, generasjonId)

        val behandlingId = generasjonId.behandlingId

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.KomplettFraBruker, behandling.behandlingStatus)

        avsluttetUtenVedtak.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.AvsluttetUtenVedtak, behandling.behandlingStatus)
    }

    @Test
    fun `start og slutt for forkastet periode`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val generasjonId = UUID.randomUUID()
        val aktørId = "1234"
        val innsendt = LocalDateTime.now()
        val registrert = innsendt.plusDays(1)

        val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), LocalDateTime.now(),
            blob, vedtaksperiodeId, generasjonId, aktørId, innsendt, registrert)

        val generasjonForkastet = GenerasjonForkastet(UUID.randomUUID(), LocalDateTime.now(), blob, generasjonId)
        val behandlingId = generasjonId.behandlingId

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.KomplettFraBruker, behandling.behandlingStatus)

        generasjonForkastet.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.BehandlesIInfotrygd, behandling.behandlingStatus)
    }

    @Test
    fun `en omgjøring av auu`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val generasjonId = UUID.randomUUID()
        val aktørId = "1234"
        val innsendt = LocalDateTime.now()
        val registrert = innsendt.plusDays(1)

        val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), LocalDateTime.now(),
            blob, vedtaksperiodeId, generasjonId, aktørId, innsendt, registrert)

        val avsluttetUtenVedtak = AvsluttetUtenVedtak(UUID.randomUUID(), LocalDateTime.now(), blob, generasjonId)

        val behandlingId = generasjonId.behandlingId

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.KomplettFraBruker, behandling.behandlingStatus)
        assertNull(behandling.relatertBehandlingId)

        avsluttetUtenVedtak.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.AvsluttetUtenVedtak, behandling.behandlingStatus)
        assertNull(behandling.relatertBehandlingId)

        val generasjonId2 = UUID.randomUUID()
        val generasjonOpprettet2 = GenerasjonOpprettet(UUID.randomUUID(), LocalDateTime.now(),
            blob, vedtaksperiodeId, generasjonId2, aktørId, innsendt, registrert)
        generasjonOpprettet2.håndter(behandlingDao)

        val behandlingId2 = generasjonId2.behandlingId
        val behandling2 = checkNotNull(behandlingDao.hent(behandlingId2))
        assertEquals(Behandling.BehandlingStatus.KomplettFraBruker, behandling2.behandlingStatus)
        assertEquals(behandlingId, behandling2.relatertBehandlingId)
    }

   internal companion object {
       val blob = jacksonObjectMapper().createObjectNode()
       internal val UUID.behandlingId get() = BehandlingId(this)
       internal class InMemoryBehandlingDao: BehandlingDao {
           private val behandlinger = mutableListOf<Behandling>()
           override fun initialiser(behandlingId: BehandlingId): Behandling.Builder? {
               val siste = hent(behandlingId) ?: return null
               return Behandling.Builder(siste)
           }
           override fun lagre(behandling: Behandling) {
               behandlinger.add(behandling)
           }
           override fun hent(behandlingId: BehandlingId) = behandlinger.lastOrNull { it.behandlingId == behandlingId }
           override fun forrigeBehandlingId(sakId: SakId) = behandlinger.lastOrNull { it.sakId == sakId }?.behandlingId
       }
   }
}