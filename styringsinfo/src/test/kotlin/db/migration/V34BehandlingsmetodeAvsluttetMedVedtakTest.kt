package db.migration

import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.*

@Disabled("Vi lytter på vedtak_fattet i stedet for avsluttet_med_vedtak og testene kjører derfor ikke")
internal class V34BehandlingsmetodeAvsluttetMedVedtakTest: BehandlingshendelseJsonMigreringTest(
    migrering = V34__behandlingsmetode_avsluttet_med_vedtak(),
    dataSource = dataSource
) {
    @Test
    fun `skal skrive om alle avsluttet_med_vedtak-hendelser sin behandlingsmetode fra null til AUTOMATISK`() {
        val behandlingId = UUID.randomUUID()
        val hendelsefabrikk = Hendelsefabrikk(behandlingId = BehandlingId(behandlingId))
        val korrigertHendelse = leggTilBehandlingshendelse(
            sakId = UUID.randomUUID(),
            behandlingId = behandlingId,
            siste = true,
            versjon = Versjon.of("0.1.0"),
            erKorrigert = false, data = {
                it.putNull("behandlingsmetode")
            },
            hendelse = hendelsefabrikk.vedtakFattet()
        )

        migrer()
        assertKorrigert(korrigertHendelse) { _, ny ->
            assertEquals("AUTOMATISK", ny.path("behandlingsmetode").asText())
        }
    }

}