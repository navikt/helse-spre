package db.migration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

internal class V44RenameBehandlingstypeOmgjøringTest: BehandlingshendelseJsonMigreringTest(
    migrering = V44__rename_behandlingstype_omgjøring()
) {

    @Test
    fun `renamer behandlingstype OMGJØRING til GJENÅPNING`() {
        val opprettetRad = leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.putNull("behandlingsresultat")
            data.put("behandlingstatus", "REGISTRERT")
            data.put("behandlingstype", "OMGJØRING")

        }
        migrer()

        assertKorrigert(opprettetRad) { gammel, ny ->
            assertEquals("OMGJØRING", gammel.path("behandlingstype").asText())
            assertEquals("GJENÅPNING", ny.path("behandlingstype").asText())
        }
    }
}