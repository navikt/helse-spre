package db.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.*

@Disabled("Vi lytter på vedtak_fattet i stedet for avsluttet_med_vedtak og testene kjører derfor ikke")
internal class V34BehandlingsmetodeAvsluttetMedVedtakTest: BehandlingshendelseJsonMigreringTest(
    migrering = V34__behandlingsmetode_avsluttet_med_vedtak(),
    dataSource = dataSource
) {
    @Test
    fun `skal skrive om alle avsluttet_med_vedtak-hendelser sin behandlingsmetode fra null til AUTOMATISK`() {
        val hendelseId = UUID.randomUUID()
        val korrigertHendelse = leggTilBehandlingshendelse(
            UUID.randomUUID(), hendelseId, true, Versjon.of("0.1.0"), false, data = {
                it.putNull("behandlingsmetode")
            },
            hendelse = VedtakFattet(
                id = hendelseId,
                opprettet = OffsetDateTime.now(),
                data = jacksonObjectMapper().createObjectNode() as JsonNode,
                behandlingId = UUID.randomUUID()
            )
        )

        migrer()
        assertKorrigert(korrigertHendelse) { _, ny ->
            assertEquals("AUTOMATISK", ny.path("behandlingsmetode").asText())
        }
    }

}