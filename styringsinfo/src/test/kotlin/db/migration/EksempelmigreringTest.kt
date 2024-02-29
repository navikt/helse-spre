package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

internal class EksempelmigreringTest: BehandlingshendelseJsonMigreringTest(
    migrering = V1337__Eksempelmigrering(),
    forrigeVersjon = MigrationVersion.LATEST,
    dataSource = dataSource
) {
    @Test
    fun `Migrerer riktige rader`() {
        val behandlingId1 = UUID.randomUUID()

        leggTilBehandlingshendelse(behandlingId = behandlingId1, siste = false, versjon = Versjon.of("1.0.0"))
        val rad2 = leggTilBehandlingshendelse(behandlingId = behandlingId1, siste = false, versjon = versjonSomSkalMigreres) { it.put("endretFelt", 2).put("fjernFelt", true) }
        val rad3 = leggTilBehandlingshendelse(behandlingId = behandlingId1, siste = false, versjon = versjonSomSkalMigreres) { it.put("endretFelt", 3).put("fjernFelt", true) }
        val rad4 = leggTilBehandlingshendelse(behandlingId = behandlingId1, siste = true, versjon = versjonSomSkalMigreres) { it.put("endretFelt", 4).put("fjernFelt", true) }
        migrer()
        assertKorrigerte(rad2, rad3)
        assertKorrigert(rad4) { _, ny->
            assertEquals("kult", ny.path("nyttFelt").asText())
            assertFalse(ny.has("fjernFelt"))
            assertEquals(1337, ny.path("endretFelt").asInt())
        }
    }

    @Test
    fun `ignorerer tidligere korrigerte rader`() {
        val behandlingId1 = UUID.randomUUID()
        leggTilBehandlingshendelse(behandlingId = behandlingId1, siste = false, versjon = versjonSomSkalMigreres, erKorrigert = true) { it.put("endretFelt", 1) }
        leggTilBehandlingshendelse(behandlingId = behandlingId1, siste = false, versjon = versjonSomSkalMigreres, erKorrigert = true) { it.put("endretFelt", 1) }
        val rad3 = leggTilBehandlingshendelse(behandlingId = behandlingId1, siste = false, versjon = versjonSomSkalMigreres, erKorrigert = false) { it.put("endretFelt", 1) }
        migrer()
        assertKorrigerte(rad3)
    }

    @Test
    fun `migrering over flere batcher`() {
        val behandlingId1 = UUID.randomUUID()
        val rader = (1..57).map { leggTilBehandlingshendelse(behandlingId = behandlingId1, siste = it == 57, versjon = versjonSomSkalMigreres) }
        migrer()
        assertKorrigerte(*rader.toTypedArray())
    }

    private companion object {
        private val versjonSomSkalMigreres = Versjon.of("4.1.1")

        private class V1337__Eksempelmigrering: BehandlingshendelseJsonMigrering() {
            override fun query() = "select sekvensnummer, data, er_korrigert from behandlingshendelse where versjon='$versjonSomSkalMigreres'"
            override fun nyVersjon() = Versjon.of("5.0.0")
            override fun nyData(gammelData: ObjectNode): ObjectNode {
                gammelData.put("nyttFelt", "kult")
                gammelData.remove("fjernFelt")
                gammelData.put("endretFelt", 1337)
                return gammelData
            }

            override val batchSize = 7
        }
    }
}


