package db.migration

import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest.Companion.dataSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

internal class V46FjerneBehandlingstatusVurdererInngangsvilkårTest: BehandlingshendelseJsonMigreringTest(
    migrering = V46__fjerne_behandlingstatus_vurderer_inngangsvilkår(),
    dataSource = dataSource
) {

    @Test
    fun `endrer de som har behandlingstatus VURDERER_INNGANGSVILKÅR tilbake til REGISTRERT`() {
        val radSomSkalKorrigeres = leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.put("behandlingstatus", "VURDERER_INNGANGSVILKÅR")
        }

        leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.put("behandlingstatus", "AVVENTER_GODKJENNING")
        }

        leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.put("behandlingstatus", "AVSLUTTET")
        }

        migrer()

        assertKorrigert(radSomSkalKorrigeres) { gammel, ny ->
            assertEquals("VURDERER_INNGANGSVILKÅR", gammel.path("behandlingstatus").asText())
            assertEquals("REGISTRERT", ny.path("behandlingstatus").asText())
        }
    }
}