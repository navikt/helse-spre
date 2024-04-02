package db.migration

import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

internal class V36UkjentMottakerBlirNullTest: BehandlingshendelseJsonMigreringTest(
    migrering = V36__ukjent_mottaker_blir_null(),
    dataSource = dataSource
) {
    @Test
    fun `skal skrive om revurderinger av førstegangsbehandlinger skal ha periodetype førstebehandling`() {
        val sakId = UUID.randomUUID()
        val behandlingId = UUID.randomUUID()
        val hendelsefabrikk = Hendelsefabrikk(SakId(sakId), BehandlingId(behandlingId))
        val behandling = leggTilBehandlingshendelse(
            sakId, behandlingId, true, Versjon.of("0.1.0"), false, data = {
                it.put("mottaker", "UKJENT")
            },
            hendelse = hendelsefabrikk.vedtakFattet()
        )

        migrer()
        assertKorrigert(behandling) { _, ny ->
            assertTrue(ny.path("mottaker").isMissingOrNull())
        }
    }

}