package db.migration

import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

internal class V35BehandlingsmetodeAvsluttetMedVedtakTest: BehandlingshendelseJsonMigreringTest(
    migrering = V35__riktig_periodetype_for_revurderte_førstegangsbehandlinger(),
    dataSource = dataSource
) {
    @Test
    fun `skal skrive om revurderinger av førstegangsbehandlinger skal ha periodetype førstebehandling`() {
        val sakId = UUID.randomUUID()
        val behandlingId = UUID.randomUUID()
        val hendelsefabrikk = Hendelsefabrikk(SakId(sakId), BehandlingId(behandlingId))
        leggTilBehandlingshendelse(
            sakId = sakId,
            behandlingId = behandlingId,
            siste = true,
            versjon = Versjon.of("0.1.0"),
            erKorrigert = false, data = {
                it.put("periodetype", "FØRSTEGANGSBEHANDLING")
            },
            hendelse = hendelsefabrikk.vedtakFattet()
        )
        val behandlingId2 = UUID.randomUUID()
        val revurdering = leggTilBehandlingshendelse(
            sakId = sakId,
            behandlingId = behandlingId2,
            siste = true,
            versjon = Versjon.of("0.1.0"),
            erKorrigert = false, data = {
                it.put("periodetype", "FORLENGELSE")
            },
            hendelse = hendelsefabrikk.vedtakFattet(behandlingId = BehandlingId(behandlingId2))
        )

        migrer()
        assertKorrigert(revurdering) { _, ny ->
            assertEquals("FØRSTEGANGSBEHANDLING", ny.path("periodetype").asText())
        }
    }

}