package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.behandling.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.PostgresBehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.asSakId
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

internal class RelatertBehandlingidTest: BehandlingshendelseJsonMigreringTest(
    migrering = V1337__Behandling1Migrering(),
    forrigeVersjon = MigrationVersion.LATEST,
    dataSource = dataSource
) {

    private val behandlingshendelseDao: BehandlingshendelseDao = PostgresBehandlingshendelseDao(dataSource)

    @Test
    fun `finner riktig relatertBehandlingId etter migrering av tidligere behandling`() {
        val sakId = UUID.randomUUID()
        val behandlingId2 = UUID.randomUUID()

        // Legger til hendelse for behandling 1
        val rad1 = leggTilBehandlingshendelse(sakId = sakId, behandlingId = behandlingId1)

        // Legger til hendelse for behandling 2, for samme sak som for behandling 1
        leggTilBehandlingshendelse(sakId = sakId, behandlingId = behandlingId2)

        assertEquals(
            BehandlingId(behandlingId2),
            behandlingshendelseDao.behandlingIdFraForrigeBehandlingshendelse(sakId.asSakId())
        )

        // Migrerer kun behandling 1
        migrer()
        assertKorrigert(rad1)

        assertEquals(
            BehandlingId(behandlingId2),
            behandlingshendelseDao.behandlingIdFraForrigeBehandlingshendelse(sakId.asSakId())
        )
    }

    private companion object {
        private val behandlingId1 = UUID.randomUUID()

        private class V1337__Behandling1Migrering: BehandlingshendelseJsonMigrering() {
            override fun query() = "select sekvensnummer, data, er_korrigert from behandlingshendelse where behandlingid='$behandlingId1'"
            override fun nyVersjon() = null
            override fun nyData(gammelData: ObjectNode): ObjectNode { return gammelData }
        }
    }
}


