package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class OmgjøringTest: AbstractTeamSakTest() {

    @Test
    fun `en omgjøring av auu som blir til et vedtak`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()

        val avsluttetUtenVedtak = hendelsefabrikk.avsluttetUtenVedtak(behandlingId)

        assertUkjentBehandling(behandlingId)
        var behandling = behandlingOpprettet.håndter(behandlingId)
        assertEquals(Behandling.Behandlingstype.SØKNAD, behandling.behandlingstype)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)
        assertNull(behandling.relatertBehandlingId)

        behandling = avsluttetUtenVedtak.håndter(behandlingId)
        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.HENLAGT, behandling.behandlingsresultat)
        assertNull(behandling.relatertBehandlingId)

        val (behandlingId2, behandlingOpprettet2) = hendelsefabrikk.behandlingOpprettet(behandlingId = Hendelsefabrikk.nyBehandlingId(), avsender = Hendelsefabrikk.Arbeidsgiver, behandlingstype = Hendelsefabrikk.Omgjøring)
        var behandling2 = behandlingOpprettet2.håndter(behandlingId2)

        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling2.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.OMGJØRING, behandling2.behandlingstype)
        assertEquals(Behandling.Behandlingskilde.ARBEIDSGIVER, behandling2.behandlingskilde)
        assertEquals(behandlingId, behandling2.relatertBehandlingId)

        hendelsefabrikk.vedtaksperiodeEndretTilVilkårsprøving().håndter(behandlingId2)
        hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingId2)
        hendelsefabrikk.vedtaksperiodeGodkjent().håndter(behandlingId2)
        behandling2 = hendelsefabrikk.vedtakFattet(behandlingId2).håndter(behandlingId2)

        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, behandling2.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.OMGJØRING, behandling2.behandlingstype)
    }

    @Test
    fun `en omgjøring som kastes ut av Spleis`() {
        val sakId = Hendelsefabrikk.nySakId()
        val behandlingId = Hendelsefabrikk.nyBehandlingId()
        nyAuu(sakId = sakId, behandlingId = behandlingId)

        val hendelsefabrikk = Hendelsefabrikk(sakId)
        val (omgjøringBehandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet(behandlingstype = Hendelsefabrikk.Omgjøring, avsender = Hendelsefabrikk.Arbeidsgiver)
        val registrertOmgjøring = behandlingOpprettet.håndter(omgjøringBehandlingId)

        assertEquals(Behandling.Behandlingstatus.REGISTRERT, registrertOmgjøring.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.OMGJØRING, registrertOmgjøring.behandlingstype)
        assertEquals(Behandling.Behandlingskilde.ARBEIDSGIVER, registrertOmgjøring.behandlingskilde)

        val forkastetOmgjøring = hendelsefabrikk.behandlingForkastet(omgjøringBehandlingId).håndter(omgjøringBehandlingId)

        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, forkastetOmgjøring.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.OMGJØRING, forkastetOmgjøring.behandlingstype)
        assertEquals(Behandling.Behandlingskilde.ARBEIDSGIVER, forkastetOmgjøring.behandlingskilde)
        assertEquals(Behandling.Behandlingsresultat.AVBRUTT, forkastetOmgjøring.behandlingsresultat)
    }
}