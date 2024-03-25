package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVVENTER_GODKJENNING
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet.Companion.Tag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class TeamSakTest: AbstractTeamSakTest() {

    @Test
    fun `funksjonell lik behandling`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()

        assertEquals(0, behandlingId.rader)
        behandlingOpprettet.håndter(behandlingId)
        assertEquals(1, behandlingId.rader)

        val vedtakFattet = hendelsefabrikk.vedtakFattet()
        vedtakFattet.håndter(behandlingId)
        assertEquals(2, behandlingId.rader)

        vedtakFattet.håndter(behandlingId)
        assertEquals(2, behandlingId.rader)
    }

    @Test
    fun `start og slutt for vedtak`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertUkjentBehandling(behandlingId)
        var behandling = behandlingOpprettet.håndter(behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        behandling = hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingId)
        assertEquals(AVVENTER_GODKJENNING, behandling.behandlingstatus)

        behandling = hendelsefabrikk.vedtakFattet(tags = listOf(Tag.Arbeidsgiverutbetaling, Tag.Innvilget)).håndter(behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Mottaker.ARBEIDSGIVER, behandling.mottaker)
        assertEquals(Behandling.Behandlingsresultat.INNVILGET, behandling.behandlingsresultat)
    }

    @Test
    fun `start og slutt for auu`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertUkjentBehandling(behandlingId)
        var behandling = behandlingOpprettet.håndter(behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val avsluttetUtenVedtak = hendelsefabrikk.avsluttetUtenVedtak()
        behandling = avsluttetUtenVedtak.håndter(behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.IKKE_REALITETSBEHANDLET, behandling.behandlingsresultat)
    }
}