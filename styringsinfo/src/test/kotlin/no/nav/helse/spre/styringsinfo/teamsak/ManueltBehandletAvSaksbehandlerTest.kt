package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.AVBRUTT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ManueltBehandletAvSaksbehandlerTest: AbstractTeamSakTest() {

    @Test
    fun `start og slutt for utkast til vedtak som avvises av saksbehandler`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertUkjentBehandling(behandlingId)
        behandlingOpprettet.håndter(behandlingId)

        hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingId)

        val behandling = hendelsefabrikk.vedtaksperiodeAvvist().håndter(behandlingId)
        assertEquals(MANUELL, behandling.behandlingsmetode)
        assertEquals(AVBRUTT, behandling.behandlingsresultat)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals("SB123", behandling.saksbehandlerEnhet)
        assertNull(behandling.beslutterEnhet)
    }

    @Test
    fun `start og slutt for godkjent vedtak av saksbehandler`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertUkjentBehandling(behandlingId)
        behandlingOpprettet.håndter(behandlingId)

        hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingId)

        var behandling = hendelsefabrikk.vedtaksperiodeGodkjent().håndter(behandlingId)
        assertEquals(MANUELL, behandling.behandlingsmetode)
        assertNull(behandling.behandlingsresultat)

        behandling = hendelsefabrikk.vedtakFattet().håndter(behandlingId)
        assertEquals(AUTOMATISK, behandling.hendelsesmetode)
        assertEquals(MANUELL, behandling.behandlingsmetode)
        assertEquals(Behandling.Behandlingsresultat.INNVILGET, behandling.behandlingsresultat)
    }


    @Test
    fun `når to saksbehandlere har behandlet saken blir det behandlingsmetode totrinns`() {
        val (behandling) = nyttVedtak(totrinnsbehandling = true)
        assertEquals(TOTRINNS, behandling.behandlingsmetode)
        assertEquals(AUTOMATISK, behandling.hendelsesmetode)
    }
}