package db.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtaksperiodeGodkjent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.*

internal class V32BehandlingsstatusVedtaksperiodeGodkjentTest: BehandlingshendelseJsonMigreringTest(
    migrering = V32__behandlingsstatus_vedtaksperiode_godkjent(),
    dataSource = dataSource
) {
    @Test
    fun `skal skrive om alle vedtaksperiode_godkjent-hendelser sin behandlingsstatus fra AVSLUTTET til GODKJENT`() {
        val hendelseId = UUID.randomUUID()
        val korrigertHendelse = leggTilBehandlingshendelse(
            UUID.randomUUID(), hendelseId, true, Versjon.of("0.1.0"), false, data = {
                it.put("behandlingstatus", "AVSLUTTET")
            },
            hendelse = VedtaksperiodeGodkjent(
                id = hendelseId,
                opprettet = OffsetDateTime.now(),
                data = jacksonObjectMapper().createObjectNode() as JsonNode,
                behandlingId = UUID.randomUUID(),
                saksbehandlerEnhet = null,
                beslutterEnhet = null,
                automatiskBehandling = true,
                totrinnsbehandling = false
            )
        )

        migrer()
        assertKorrigert(korrigertHendelse) { _, ny ->
            Assertions.assertEquals("GODKJENT", ny.path("behandlingstatus").asText())
        }
    }

}