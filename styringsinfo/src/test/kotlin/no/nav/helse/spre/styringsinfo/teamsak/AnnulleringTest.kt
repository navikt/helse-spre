package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class AnnulleringTest: AbstractTeamSakTest() {

    @Test
    fun `sak annulleres`() {
        val sakId = Hendelsefabrikk.nySakId()
        nyttVedtak(sakId = sakId)

        val annulleringHendelsefabrikk = Hendelsefabrikk(sakId = sakId)
        val (behandlingIdAnnullert, behandlingOpprettet) = annulleringHendelsefabrikk.behandlingOpprettet(sakId = sakId, behandlingstype = Hendelsefabrikk.Revurdering)

        val registrertAnnullering= behandlingOpprettet.håndter(behandlingIdAnnullert)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, registrertAnnullering.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.REVURDERING, registrertAnnullering.behandlingstype)
        assertNull(registrertAnnullering.behandlingsresultat)

        val annullertBehandling = annulleringHendelsefabrikk.vedtaksperiodeAnnullert(behandlingIdAnnullert).håndter(behandlingIdAnnullert)
        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, annullertBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.REVURDERING, annullertBehandling.behandlingstype)
        assertEquals(Behandling.Behandlingsresultat.ANNULLERT, annullertBehandling.behandlingsresultat)

        val forkastetBehandling = annulleringHendelsefabrikk.behandlingForkastet(behandlingIdAnnullert).håndter(behandlingIdAnnullert)
        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, forkastetBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.REVURDERING, forkastetBehandling.behandlingstype)
        assertEquals(Behandling.Behandlingsresultat.ANNULLERT, forkastetBehandling.behandlingsresultat)
    }

    @Test
    fun `annullering av en tidligere utbetalt periode`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (januarBehandlingId, januarBehandlingOpprettet) = hendelsefabrikk.behandlingOpprettet(avsender = Hendelsefabrikk.Saksbehandler)

        var utbetaltBehandling = januarBehandlingOpprettet.håndter(januarBehandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, utbetaltBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, utbetaltBehandling.behandlingstype)
        assertNull(utbetaltBehandling.behandlingsresultat)
        assertEquals(utbetaltBehandling.behandlingsmetode, Behandling.Metode.AUTOMATISK)
        assertEquals(utbetaltBehandling.hendelsesmetode, Behandling.Metode.MANUELL)


        val januarVedtakFattet = hendelsefabrikk.vedtakFattet()
        utbetaltBehandling = januarVedtakFattet.håndter(januarBehandlingId)
        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, utbetaltBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, utbetaltBehandling.behandlingstype)
        assertEquals(Behandling.Behandlingsresultat.INNVILGET, utbetaltBehandling.behandlingsresultat)
        assertEquals(Behandling.Metode.AUTOMATISK, utbetaltBehandling.behandlingsmetode)

        val (annulleringBehandlingId, januarAnnullertBehandlingOpprettet) = hendelsefabrikk.behandlingOpprettet(behandlingId = Hendelsefabrikk.nyBehandlingId(), behandlingstype = Hendelsefabrikk.Revurdering, avsender = Hendelsefabrikk.Saksbehandler)
        var annullertBehandling = januarAnnullertBehandlingOpprettet.håndter(annulleringBehandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, annullertBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.REVURDERING, annullertBehandling.behandlingstype)
        assertEquals(Behandling.Behandlingskilde.SAKSBEHANDLER, annullertBehandling.behandlingskilde)
        assertNull(annullertBehandling.behandlingsresultat)

        hendelsefabrikk.vedtaksperiodeAnnullert(annulleringBehandlingId).håndter(annulleringBehandlingId)

        val behandlingForkastet = hendelsefabrikk.behandlingForkastet(annulleringBehandlingId)
        annullertBehandling = behandlingForkastet.håndter(annulleringBehandlingId)
        utbetaltBehandling = behandling(januarBehandlingId)

        assertEquals(2, januarBehandlingId.rader) // Registrert, Vedtatt
        assertEquals(2, annulleringBehandlingId.rader) // Registrert, Avbrutt

        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, utbetaltBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, utbetaltBehandling.behandlingstype)
        assertEquals(Behandling.Behandlingsresultat.INNVILGET, utbetaltBehandling.behandlingsresultat)
        assertEquals(Behandling.Metode.AUTOMATISK, utbetaltBehandling.behandlingsmetode)

        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, annullertBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.REVURDERING, annullertBehandling.behandlingstype)
        assertEquals(Behandling.Behandlingsresultat.ANNULLERT, annullertBehandling.behandlingsresultat)
        assertEquals(Behandling.Metode.MANUELL, annullertBehandling.behandlingsmetode)
    }
}