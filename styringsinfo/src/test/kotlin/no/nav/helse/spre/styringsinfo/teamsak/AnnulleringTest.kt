package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingskilde.SAKSBEHANDLER
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.ANNULLERT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.INNVILGET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.REGISTRERT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstype.REVURDERING
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstype.SØKNAD
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.MANUELL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class AnnulleringTest: AbstractTeamSakTest() {

    @Test
    fun `selvstendig - sak annulleres`() {
        val sakId = Hendelsefabrikk.nySakId()
        nyttVedtak(sakId = sakId)

        val annulleringHendelsefabrikk = Hendelsefabrikk(sakId = sakId, yrkesaktivitetstype = "SELVSTENDIG")
        val (behandlingIdAnnullert, behandlingOpprettet) = annulleringHendelsefabrikk.behandlingOpprettet(sakId = sakId, behandlingstype = Hendelsefabrikk.Revurdering)

        val registrertAnnullering = behandlingOpprettet.håndter(behandlingIdAnnullert)
        assertEquals(REGISTRERT, registrertAnnullering.behandlingstatus)
        assertEquals(REVURDERING, registrertAnnullering.behandlingstype)
        assertNull(registrertAnnullering.behandlingsresultat)
        assertEquals("SELVSTENDIG", registrertAnnullering.yrkesaktivitetstype)

        val annullertBehandling = annulleringHendelsefabrikk.vedtaksperiodeAnnullert(behandlingIdAnnullert).håndter(behandlingIdAnnullert)
        assertEquals(AVSLUTTET, annullertBehandling.behandlingstatus)
        assertEquals(REVURDERING, annullertBehandling.behandlingstype)
        assertEquals(ANNULLERT, annullertBehandling.behandlingsresultat)
        assertEquals("SELVSTENDIG", annullertBehandling.yrkesaktivitetstype)

        val forkastetBehandling = annulleringHendelsefabrikk.behandlingForkastet(behandlingIdAnnullert).håndter(behandlingIdAnnullert)
        assertEquals(AVSLUTTET, forkastetBehandling.behandlingstatus)
        assertEquals(REVURDERING, forkastetBehandling.behandlingstype)
        assertEquals(ANNULLERT, forkastetBehandling.behandlingsresultat)
        assertEquals("SELVSTENDIG", forkastetBehandling.yrkesaktivitetstype)

        assertEquals(MANUELL, forkastetBehandling.behandlingsmetode)
        assertNull(forkastetBehandling.saksbehandlerEnhet)
    }


    @Test
    fun `sak annulleres`() {
        val sakId = Hendelsefabrikk.nySakId()
        nyttVedtak(sakId = sakId)

        val annulleringHendelsefabrikk = Hendelsefabrikk(sakId = sakId)
        val (behandlingIdAnnullert, behandlingOpprettet) = annulleringHendelsefabrikk.behandlingOpprettet(sakId = sakId, behandlingstype = Hendelsefabrikk.Revurdering)

        val registrertAnnullering = behandlingOpprettet.håndter(behandlingIdAnnullert)
        assertEquals(REGISTRERT, registrertAnnullering.behandlingstatus)
        assertEquals(REVURDERING, registrertAnnullering.behandlingstype)
        assertNull(registrertAnnullering.behandlingsresultat)
        assertEquals("ARBEIDSTAKER", registrertAnnullering.yrkesaktivitetstype)

        val annullertBehandling = annulleringHendelsefabrikk.vedtaksperiodeAnnullert(behandlingIdAnnullert).håndter(behandlingIdAnnullert)
        assertEquals(AVSLUTTET, annullertBehandling.behandlingstatus)
        assertEquals(REVURDERING, annullertBehandling.behandlingstype)
        assertEquals(ANNULLERT, annullertBehandling.behandlingsresultat)
        assertEquals("ARBEIDSTAKER", annullertBehandling.yrkesaktivitetstype)

        val forkastetBehandling = annulleringHendelsefabrikk.behandlingForkastet(behandlingIdAnnullert).håndter(behandlingIdAnnullert)
        assertEquals(AVSLUTTET, forkastetBehandling.behandlingstatus)
        assertEquals(REVURDERING, forkastetBehandling.behandlingstype)
        assertEquals(ANNULLERT, forkastetBehandling.behandlingsresultat)
        assertEquals("ARBEIDSTAKER", forkastetBehandling.yrkesaktivitetstype)

        assertEquals(MANUELL, forkastetBehandling.behandlingsmetode)
        assertNull(forkastetBehandling.saksbehandlerEnhet)
    }

    @Test
    fun `annullering av en tidligere utbetalt periode`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (januarBehandlingId, januarBehandlingOpprettet) = hendelsefabrikk.behandlingOpprettet(avsender = Hendelsefabrikk.Saksbehandler)

        var utbetaltBehandling = januarBehandlingOpprettet.håndter(januarBehandlingId)
        assertEquals(REGISTRERT, utbetaltBehandling.behandlingstatus)
        assertEquals(SØKNAD, utbetaltBehandling.behandlingstype)
        assertNull(utbetaltBehandling.behandlingsresultat)
        assertEquals(utbetaltBehandling.behandlingsmetode, AUTOMATISK)
        assertEquals(utbetaltBehandling.hendelsesmetode, MANUELL)


        val januarVedtakFattet = hendelsefabrikk.vedtakFattet()
        utbetaltBehandling = januarVedtakFattet.håndter(januarBehandlingId)
        assertEquals(AVSLUTTET, utbetaltBehandling.behandlingstatus)
        assertEquals(SØKNAD, utbetaltBehandling.behandlingstype)
        assertEquals(INNVILGET, utbetaltBehandling.behandlingsresultat)
        assertEquals(AUTOMATISK, utbetaltBehandling.behandlingsmetode)

        val (annulleringBehandlingId, januarAnnullertBehandlingOpprettet) = hendelsefabrikk.behandlingOpprettet(behandlingId = Hendelsefabrikk.nyBehandlingId(), behandlingstype = Hendelsefabrikk.Revurdering, avsender = Hendelsefabrikk.Saksbehandler)
        var annullertBehandling = januarAnnullertBehandlingOpprettet.håndter(annulleringBehandlingId)
        assertEquals(REGISTRERT, annullertBehandling.behandlingstatus)
        assertEquals(REVURDERING, annullertBehandling.behandlingstype)
        assertEquals(SAKSBEHANDLER, annullertBehandling.behandlingskilde)
        assertNull(annullertBehandling.behandlingsresultat)

        hendelsefabrikk.vedtaksperiodeAnnullert(annulleringBehandlingId).håndter(annulleringBehandlingId)

        val behandlingForkastet = hendelsefabrikk.behandlingForkastet(annulleringBehandlingId)
        annullertBehandling = behandlingForkastet.håndter(annulleringBehandlingId)
        utbetaltBehandling = behandling(januarBehandlingId)

        assertEquals(2, januarBehandlingId.rader) // Registrert, Vedtatt
        assertEquals(2, annulleringBehandlingId.rader) // Registrert, Avbrutt

        assertEquals(AVSLUTTET, utbetaltBehandling.behandlingstatus)
        assertEquals(SØKNAD, utbetaltBehandling.behandlingstype)
        assertEquals(INNVILGET, utbetaltBehandling.behandlingsresultat)
        assertEquals(AUTOMATISK, utbetaltBehandling.behandlingsmetode)

        assertEquals(AVSLUTTET, annullertBehandling.behandlingstatus)
        assertEquals(REVURDERING, annullertBehandling.behandlingstype)
        assertEquals(ANNULLERT, annullertBehandling.behandlingsresultat)
        assertEquals(MANUELL, annullertBehandling.behandlingsmetode)
    }
}
