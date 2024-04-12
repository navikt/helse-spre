package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingskilde.ARBEIDSGIVER
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.AVBRUTT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.IKKE_REALITETSBEHANDLET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.REGISTRERT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstype.GJENÅPNING
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstype.SØKNAD
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
        assertEquals(SØKNAD, behandling.behandlingstype)
        assertEquals(REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)
        assertNull(behandling.relatertBehandlingId)

        behandling = avsluttetUtenVedtak.håndter(behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(IKKE_REALITETSBEHANDLET, behandling.behandlingsresultat)
        assertNull(behandling.relatertBehandlingId)

        val (behandlingId2, behandlingOpprettet2) = hendelsefabrikk.behandlingOpprettet(
            behandlingId = Hendelsefabrikk.nyBehandlingId(),
            avsender = Hendelsefabrikk.Arbeidsgiver,
            behandlingstype = Hendelsefabrikk.Omgjøring)
        var behandling2 = behandlingOpprettet2.håndter(behandlingId2)

        assertEquals(REGISTRERT, behandling2.behandlingstatus)
        assertEquals(GJENÅPNING, behandling2.behandlingstype)
        assertEquals(ARBEIDSGIVER, behandling2.behandlingskilde)
        assertEquals(behandlingId, behandling2.relatertBehandlingId)

        hendelsefabrikk.utkastTilVedtak(behandlingId = behandlingId2).håndter(behandlingId2)
        hendelsefabrikk.vedtaksperiodeGodkjent(behandlingId = behandlingId2).håndter(behandlingId2)
        behandling2 = hendelsefabrikk.vedtakFattet(behandlingId = behandlingId2).håndter(behandlingId2)

        assertEquals(AVSLUTTET, behandling2.behandlingstatus)
        assertEquals(GJENÅPNING, behandling2.behandlingstype)
    }

    @Test
    fun `en omgjøring som kastes ut av Spleis`() {
        val sakId = Hendelsefabrikk.nySakId()
        val behandlingId = Hendelsefabrikk.nyBehandlingId()
        nyAuu(sakId = sakId, behandlingId = behandlingId)

        val hendelsefabrikk = Hendelsefabrikk(sakId)
        val (omgjøringBehandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet(behandlingstype = Hendelsefabrikk.Omgjøring, avsender = Hendelsefabrikk.Arbeidsgiver)
        val registrertOmgjøring = behandlingOpprettet.håndter(omgjøringBehandlingId)

        assertEquals(REGISTRERT, registrertOmgjøring.behandlingstatus)
        assertEquals(GJENÅPNING, registrertOmgjøring.behandlingstype)
        assertEquals(ARBEIDSGIVER, registrertOmgjøring.behandlingskilde)

        val forkastetOmgjøring = hendelsefabrikk.behandlingForkastet(omgjøringBehandlingId).håndter(omgjøringBehandlingId)

        assertEquals(AVSLUTTET, forkastetOmgjøring.behandlingstatus)
        assertEquals(GJENÅPNING, forkastetOmgjøring.behandlingstype)
        assertEquals(ARBEIDSGIVER, forkastetOmgjøring.behandlingskilde)
        assertEquals(AVBRUTT, forkastetOmgjøring.behandlingsresultat)
    }
}