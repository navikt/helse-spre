package no.nav.helse.spre.styringsinfo

import java.util.UUID
import no.nav.helse.spre.styringsinfo.teamsak.AbstractTeamSakTest
import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVVENTER_GODKJENNING
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.REGISTRERT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.KOMPLETT_FAKTAGRUNNLAG
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtaksperiodeVenterDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class VedtaksperiodeVenterTest: AbstractTeamSakTest() {

    @Test
    fun `periode venter bak en annen til godkjenning`() {
        val behandlingTilGodkjenning= tilGodkjenning()
        val venterPaDenTilGodkjenning = nyPeriodeSomVenterPå(behandlingTilGodkjenning, "GODKJENNING")
        assertEquals(AVVENTER_GODKJENNING, behandlingTilGodkjenning.behandlingstatus)
        assertEquals(KOMPLETT_FAKTAGRUNNLAG, venterPaDenTilGodkjenning.behandlingstatus)
    }

    @Test
    fun `periode venter bak en annen periode som venter på noe annet enn godkjenning`() {
        val periode1= tilRegistrert()
        val periode2SomVenterPå1 = nyPeriodeSomVenterPå(periode1, "ARBEIDSGIVER")
        assertEquals(REGISTRERT, periode1.behandlingstatus)
        assertEquals(REGISTRERT, periode2SomVenterPå1.behandlingstatus)
    }

    private fun tilGodkjenning(): Behandling {
        val sakId = SakId(UUID.randomUUID())
        val behandlingId = BehandlingId(UUID.randomUUID())
        val hendelsefabrikk = Hendelsefabrikk(sakId, behandlingId)
        val (_, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        var behandling = behandlingOpprettet.håndter(behandlingId)
        assertEquals(REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)
        behandling = hendelsefabrikk.utkastTilVedtak().håndter(behandlingId)
        assertEquals(AVVENTER_GODKJENNING, behandling.behandlingstatus)
        return behandling
    }

    private fun tilRegistrert(): Behandling {
        val sakId = SakId(UUID.randomUUID())
        val behandlingId = BehandlingId(UUID.randomUUID())
        val hendelsefabrikk = Hendelsefabrikk(sakId, behandlingId)
        val (_, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        val behandling = behandlingOpprettet.håndter(behandlingId)
        assertEquals(REGISTRERT, behandling.behandlingstatus)
        return behandling
    }

    private fun nyPeriodeSomVenterPå(venterPå: Behandling, venterPåHva: String): Behandling {
        val sakId = SakId(UUID.randomUUID())
        val behandlingId = BehandlingId(UUID.randomUUID())
        val hendelsefabrikk = Hendelsefabrikk(sakId, behandlingId)
        val (_, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        var behandling = behandlingOpprettet.håndter(behandlingId)
        assertEquals(REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)
        behandling = hendelsefabrikk.vedtaksperiodeVenter(venterPå = listOf(VedtaksperiodeVenterDto(
            vedtaksperiodeId = behandling.sakId.id,
            behandlingId = behandling.behandlingId.id,
            yrkesaktivitetstype = behandling.yrkesaktivitetstype,
            venterPå = VedtaksperiodeVenterDto.VenterPå(
                vedtaksperiodeId = venterPå.sakId.id,
                venteårsak = VedtaksperiodeVenterDto.Venteårsak(venterPåHva)
            )
        ))).håndter(behandlingId)
        return behandling
    }
}
