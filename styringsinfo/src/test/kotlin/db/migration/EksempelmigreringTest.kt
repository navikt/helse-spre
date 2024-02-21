package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.flywaydb.core.api.MigrationVersion
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

        leggTilRad(behandlingId = behandlingId1, siste = false, versjon = Versjon.of("1.0.0"))
        val rad2 = leggTilRad(behandlingId = behandlingId1, siste = false, versjon = versjonSomSkalMigreres) { it.put("endretFelt", 1) }
        val rad3 = leggTilRad(behandlingId = behandlingId1, siste = false, versjon = versjonSomSkalMigreres) { it.put("endretFelt", 1) }
        val rad4 = leggTilRad(behandlingId = behandlingId1, siste = true, versjon = versjonSomSkalMigreres) { it.put("endretFelt", 1) }
        migrer()
        assertKorrigerte(rad2, rad3, rad4)
    }

    private companion object {
        private val versjonSomSkalMigreres = Versjon.of("4.1.1")

        private class V1337__Eksempelmigrering: BehandlingshendelseJsonMigrering() {
            override fun whereClause() = "versjon='$versjonSomSkalMigreres'"
            override fun nyVersjon() = Versjon.of("5.0.0")
            override fun nyData(gammelData: ObjectNode): ObjectNode {
                gammelData.put("nyttFelt", "kult")
                gammelData.remove("fjernFelt")
                gammelData.put("endretFelt", 2)
                return gammelData
            }
        }
    }
}


