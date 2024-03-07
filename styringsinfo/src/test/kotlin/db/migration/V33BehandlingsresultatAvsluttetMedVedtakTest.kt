package db.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import junit.framework.TestCase.assertEquals
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.AvsluttetMedVedtak
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class V33BehandlingsresultatAvsluttetMedVedtakTest: BehandlingshendelseJsonMigreringTest(
    migrering = V33__behandlingsresultat_avsluttet_med_vedtak(),
    dataSource = dataSource
) {
    @Test
    fun `skal skrive om alle avsluttet_med_vedtak-hendelser sitt behandlingsresultat fra VEDTATT til VEDTAK_IVERKSATT`() {
        val hendelseId = UUID.randomUUID()
        val korrigertHendelse = leggTilBehandlingshendelse(
            UUID.randomUUID(), hendelseId, true, Versjon.of("0.1.0"), false, data = {
                it.put("behandlingsresultat", "VEDTATT")
            },
            hendelse = AvsluttetMedVedtak(
                id = hendelseId,
                opprettet = LocalDateTime.now(),
                data = jacksonObjectMapper().createObjectNode() as JsonNode,
                behandlingId = UUID.randomUUID()
            )
        )

        migrer()
        assertKorrigert(korrigertHendelse) { _, ny ->
            assertEquals("VEDTAK_IVERKSATT", ny.path("behandlingsresultat").asText())
        }
    }

}