package db.migration

import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest.Companion.dataSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

internal class V40HendelsesmetodeTest: BehandlingshendelseJsonMigreringTest(
    migrering = V40__hendelsesmetode(),
    dataSource = dataSource
) {

    @Test
    fun automatisk() {
        val rad = leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.put("behandlingsmetode", "AUTOMATISK")
        }
        migrer()
        assertKorrigert(rad) { _, ny ->
            assertEquals("AUTOMATISK", ny.path("behandlingsmetode").asText())
            assertEquals("AUTOMATISK", ny.path("hendelsesmetode").asText())
        }
    }

    @Test
    fun manuell() {
        val rad = leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.put("behandlingsmetode", "MANUELL")
        }
        migrer()
        assertKorrigert(rad) { _, ny ->
            assertEquals("MANUELL", ny.path("behandlingsmetode").asText())
            assertEquals("MANUELL", ny.path("hendelsesmetode").asText())
        }
    }

    @Test
    fun `null`() {
        val rad = leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.putNull("behandlingsmetode")
        }
        migrer()
        assertKorrigert(rad) { _, ny ->
            assertTrue(ny.path("behandlingsmetode").isNull)
            assertEquals("AUTOMATISK", ny.path("hendelsesmetode").asText())
        }
    }


    @Test
    fun `ikke siste rad for behandling`() {
        val behandlingId = UUID.randomUUID()
        leggTilBehandlingshendelse(behandlingId = behandlingId, siste = false) { data ->
            data.put("behandlingsmetode", "AUTOMATISK")
        }
        val sisteRad = leggTilBehandlingshendelse(behandlingId = behandlingId, siste = true) { data ->
            data.put("behandlingsmetode", "AUTOMATISK")
        }
        migrer()
        assertKorrigert(sisteRad) { _, ny ->
            assertEquals("AUTOMATISK", ny.path("behandlingsmetode").asText())
            assertEquals("AUTOMATISK", ny.path("hendelsesmetode").asText())
        }
    }
}