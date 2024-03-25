package db.migration

import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest.Companion.dataSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

internal class V45RenameBehandlingstypeOmgjøringTest: BehandlingshendelseJsonMigreringTest(
    migrering = V45__rename_behandlingstype_omgjøring(),
    dataSource = dataSource
) {

    @Test
    fun `renamer behandlingstype OMGJØRING til GJENÅPNING`() {
        val opprettetRad = leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.putNull("behandlingsresultat")
            data.put("behandlingstatus", "REGISTRERT")
            data.put("behandlingtype", "OMGJØRING")

        }
        migrer()

        assertKorrigert(opprettetRad) { gammel, ny ->
            assertEquals("OMGJØRING", gammel.path("behandlingtype").asText())
            assertEquals("GJENÅPNING", ny.path("behandlingtype").asText())
        }
    }
}