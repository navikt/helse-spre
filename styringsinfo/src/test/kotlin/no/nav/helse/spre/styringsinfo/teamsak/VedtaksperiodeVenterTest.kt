package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtaksperiodeVenterDto
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VedtaksperiodeVenterTest: AbstractTeamSakTest() {

    @Test
    fun `periode venter bak en annen til godkjenning`() {
        val behandlingTilGodkjenning= tilGodkjenning()
        val venterPaDenTilGodkjenning = nyPeriodeSomVenterPå(behandlingTilGodkjenning, "GODKJENNING")
        Assertions.assertEquals(
            Behandling.Behandlingstatus.AVVENTER_GODKJENNING,
            behandlingTilGodkjenning.behandlingstatus
        )
        Assertions.assertEquals(
            Behandling.Behandlingstatus.KOMPLETT_FAKTAGRUNNLAG,
            venterPaDenTilGodkjenning.behandlingstatus
        )
    }

    @Test
    fun `periode venter bak en annen periode som venter på noe annet enn godkjenning`() {
        val periode1= tilRegistrert()
        val periode2SomVenterPå1 = nyPeriodeSomVenterPå(periode1, "ARBEIDSGIVER")
        Assertions.assertEquals(Behandling.Behandlingstatus.REGISTRERT, periode1.behandlingstatus)
        Assertions.assertEquals(Behandling.Behandlingstatus.REGISTRERT, periode2SomVenterPå1.behandlingstatus)
    }

    private fun tilGodkjenning(): Behandling {
        val sakId = SakId(UUID.randomUUID())
        val behandlingId = BehandlingId(UUID.randomUUID())
        val hendelsefabrikk = Hendelsefabrikk(sakId, behandlingId)
        val (_, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        var behandling = behandlingOpprettet.håndter(behandlingId)
        Assertions.assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        Assertions.assertNull(behandling.behandlingsresultat)
        behandling = hendelsefabrikk.utkastTilVedtak().håndter(behandlingId)
        Assertions.assertEquals(Behandling.Behandlingstatus.AVVENTER_GODKJENNING, behandling.behandlingstatus)
        return behandling
    }

    private fun tilRegistrert(): Behandling {
        val sakId = SakId(UUID.randomUUID())
        val behandlingId = BehandlingId(UUID.randomUUID())
        val hendelsefabrikk = Hendelsefabrikk(sakId, behandlingId)
        val (_, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        val behandling = behandlingOpprettet.håndter(behandlingId)
        Assertions.assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        return behandling
    }

    private fun nyPeriodeSomVenterPå(venterPå: Behandling, venterPåHva: String): Behandling {
        val sakId = SakId(UUID.randomUUID())
        val behandlingId = BehandlingId(UUID.randomUUID())
        val hendelsefabrikk = Hendelsefabrikk(sakId, behandlingId)
        val (_, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        var behandling = behandlingOpprettet.håndter(behandlingId)
        Assertions.assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        Assertions.assertNull(behandling.behandlingsresultat)
        behandling = hendelsefabrikk.vedtaksperiodeVenter(venterPå = listOf(
            VedtaksperiodeVenterDto(
                vedtaksperiodeId = behandling.sakId.id,
                behandlingId = behandling.behandlingId.id,
                yrkesaktivitetstype = behandling.yrkesaktivitetstype,
                venterPå = VedtaksperiodeVenterDto.VenterPå(
                    vedtaksperiodeId = venterPå.sakId.id,
                    venteårsak = VedtaksperiodeVenterDto.Venteårsak(venterPåHva)
                )
            )
        )).håndter(behandlingId)
        return behandling
    }
}
