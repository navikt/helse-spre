package no.nav.helse.spre.styringsinfo.teamsak

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest
import no.nav.helse.spre.styringsinfo.teamsak.behandling.*
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class TeamSakTest: AbstractDatabaseTest() {

    private val behandlingDao: BehandlingDao = PostgresBehandlingDao(dataSource)

    @Test
    fun `funksjonell lik behandling`() {
        val (behandlingId, generasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling)

        assertEquals(0, behandlingId.rader)
        generasjonOpprettet.håndter(behandlingDao)
        assertEquals(1, behandlingId.rader)

        val avsluttetMedVedtak = avsluttetMedVedtak(behandlingId)
        avsluttetMedVedtak.håndter(behandlingDao)
        assertEquals(2, behandlingId.rader)

        avsluttetMedVedtak.håndter(behandlingDao)
        assertEquals(2, behandlingId.rader)
    }

    @Test
    fun `start og slutt for vedtak`() {
        val (behandlingId, generasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling)
        assertNull(behandlingDao.hent(behandlingId))
        var behandling = generasjonOpprettet.håndter(behandlingDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val avsluttetMedVedtak = avsluttetMedVedtak(behandlingId)
        behandling = avsluttetMedVedtak.håndter(behandlingDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.Vedtatt, behandling.behandlingsresultat)
    }

    @Test
    fun `start og slutt for auu`() {
        val (behandlingId, generasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling)
        assertNull(behandlingDao.hent(behandlingId))
        var behandling = generasjonOpprettet.håndter(behandlingDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val avsluttetUtenVedtak = avsluttetUtenVedtak(behandlingId)
        behandling = avsluttetUtenVedtak.håndter(behandlingDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.Henlagt, behandling.behandlingsresultat)
    }

    @Test
    fun `start og slutt for forkastet periode`() {
        val (behandlingId, generasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling)
        assertNull(behandlingDao.hent(behandlingId))
        var behandling = generasjonOpprettet.håndter(behandlingDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val generasjonForkastet = generasjonForkastet(behandlingId)
        behandling = generasjonForkastet.håndter(behandlingDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.Avbrutt, behandling.behandlingsresultat)
    }

    @Test
    fun `annullering av en tidligere utbetalt periode`() {
        val (januarBehandlingId, januarGenerasjonOpprettet, januarSakId) = generasjonOpprettet(Førstegangsbehandling)

        var behandling = januarGenerasjonOpprettet.håndter(behandlingDao, januarBehandlingId)
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Førstegangsbehandling, behandling.behandlingstype)
        assertNull(behandling.behandlingsresultat)

        val januarAvsluttetMedVedtak = avsluttetMedVedtak(januarBehandlingId)
        behandling = januarAvsluttetMedVedtak.håndter(behandlingDao, januarBehandlingId)
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Førstegangsbehandling, behandling.behandlingstype)
        assertEquals(Behandling.Behandlingsresultat.Vedtatt, behandling.behandlingsresultat)

        val (annulleringBehandlingId, januarAnnullertGenerasjonOpprettet) = generasjonOpprettet(TilInfotrygd, januarSakId, avsender = GenerasjonOpprettet.Avsender("SAKSBEHANDLER"))
        behandling = januarAnnullertGenerasjonOpprettet.håndter(behandlingDao, annulleringBehandlingId)
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Førstegangsbehandling, behandling.behandlingstype)
        assertEquals(Behandling.Behandlingskilde.Saksbehandler, behandling.behandlingskilde)
        assertNull(behandling.behandlingsresultat)

        val generasjonForkastet = generasjonForkastet(annulleringBehandlingId)
        behandling = generasjonForkastet.håndter(behandlingDao, annulleringBehandlingId)

        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Førstegangsbehandling, behandling.behandlingstype)
        assertEquals(Behandling.Behandlingsresultat.Avbrutt, behandling.behandlingsresultat)
    }

    @Test
    fun `periode som blir forkastet på direkten`() {
        val (behandlingId, generasjonOpprettet) = generasjonOpprettet(TilInfotrygd)
        var behandling = generasjonOpprettet.håndter(behandlingDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Førstegangsbehandling, behandling.behandlingstype)
        assertNull(behandling.behandlingsresultat)

        val generasjonForkastet = generasjonForkastet(behandlingId)
        behandling = generasjonForkastet.håndter(behandlingDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Førstegangsbehandling, behandling.behandlingstype)
        assertEquals(Behandling.Behandlingsresultat.Avbrutt, behandling.behandlingsresultat)
    }

    @Test
    fun `en omgjøring av auu`() {
        val (behandlingId, generasjonOpprettet, sakId) = generasjonOpprettet(Førstegangsbehandling)

        val avsluttetUtenVedtak = avsluttetUtenVedtak(behandlingId)

        assertNull(behandlingDao.hent(behandlingId))
        var behandling = generasjonOpprettet.håndter(behandlingDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)
        assertNull(behandling.relatertBehandlingId)

        behandling = avsluttetUtenVedtak.håndter(behandlingDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.Henlagt, behandling.behandlingsresultat)
        assertNull(behandling.relatertBehandlingId)

        val (behandlingId2, generasjonOpprettet2) = generasjonOpprettet(Omgjøring, sakId, avsender = GenerasjonOpprettet.Avsender("ARBEIDSGIVER"))
        val behandling2 = generasjonOpprettet2.håndter(behandlingDao, behandlingId2)

        assertEquals(Behandling.Behandlingstatus.Registrert, behandling2.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Omgjøring, behandling2.behandlingstype)
        assertEquals(Behandling.Behandlingskilde.Arbeidsgiver, behandling2.behandlingskilde)
        assertEquals(behandlingId, behandling2.relatertBehandlingId)
    }

    @BeforeEach
    fun beforeEach() {
        sessionOf(dataSource).use { session ->
            session.run(queryOf("truncate table behandling;").asExecute)
        }
    }

    private val BehandlingId.rader get() =  sessionOf(dataSource).use { session ->
        session.run(queryOf("select count(1) from behandling where behandlingId='$this'").map { row -> row.int(1) }.asSingle)
    } ?: 0

    private fun Hendelse.håndter(behandlingDao: BehandlingDao, behandlingId: BehandlingId): Behandling {
        håndter(behandlingDao)
        return checkNotNull(behandlingDao.hent(behandlingId)) { "Fant ikke behandling $behandlingId" }
    }

    internal companion object {
       private val nå = LocalDateTime.now()
       private var teller = 1L
       private val nesteTidspunkt get() = nå.plusDays(teller++)

       private val objectMapper = jacksonObjectMapper()
       private val blob = objectMapper.createObjectNode()

       internal val Sykmeldt = GenerasjonOpprettet.Avsender("SYKMELDT")
       internal val Arbeidsgiver = GenerasjonOpprettet.Avsender("ARBEIDSGIVER")
       internal val Saksbehandler = GenerasjonOpprettet.Avsender("SAKSBEHANDLER")
       internal val System = GenerasjonOpprettet.Avsender("SYSTEM")

       internal val Førstegangsbehandling = GenerasjonOpprettet.Generasjonstype("Førstegangsbehandling")
       internal val TilInfotrygd = GenerasjonOpprettet.Generasjonstype("TilInfotrygd")
       internal val Omgjøring = GenerasjonOpprettet.Generasjonstype("Omgjøring")
       internal val Revurdering = GenerasjonOpprettet.Generasjonstype("Revurdering")

       internal fun generasjonOpprettet(
           generasjonstype: GenerasjonOpprettet.Generasjonstype,
           sakId: SakId = SakId(UUID.randomUUID()),
           behandlingId: BehandlingId = BehandlingId(UUID.randomUUID()),
           aktørId: String = "1234",
           avsender: GenerasjonOpprettet.Avsender = Sykmeldt
       ): Triple<BehandlingId, GenerasjonOpprettet, SakId> {
           val innsendt = nesteTidspunkt
           val registret = nesteTidspunkt
           val opprettet = nesteTidspunkt
           val generasjonkilde = GenerasjonOpprettet.Generasjonkilde(innsendt, registret, avsender)
           val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), opprettet, blob, sakId.id, behandlingId.id, aktørId, generasjonkilde, generasjonstype)
           return Triple(behandlingId, generasjonOpprettet, sakId)
       }
       internal fun avsluttetMedVedtak(behandlingId: BehandlingId) = AvsluttetMedVedtak(UUID.randomUUID(), nesteTidspunkt, blob, behandlingId.id)
       internal fun avsluttetUtenVedtak(behandlingId: BehandlingId) = AvsluttetUtenVedtak(UUID.randomUUID(), nesteTidspunkt, blob, behandlingId.id)
       internal fun generasjonForkastet(behandlingId: BehandlingId) = GenerasjonForkastet(UUID.randomUUID(), nesteTidspunkt, blob, behandlingId.id)
   }
}