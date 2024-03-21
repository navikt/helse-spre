package db.migration

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.BehandlingOpprettet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

internal class V31NavnEndringFørstegangsbehandlingTilSøknadTest: BehandlingshendelseJsonMigreringTest(
    migrering = V31__navnendring_førstegangsbehandling_til_søknad(),
    dataSource = dataSource
) {

    @Test
    fun `endrer navn fra FØRSTEGANGSBEHANDLING til SØKNAD`() {
        val behandlingId1 = UUID.randomUUID()
        val behandlingOpprettet = BehandlingOpprettet(
            id = UUID.randomUUID(),
            opprettet = OffsetDateTime.now(),
            data = jacksonObjectMapper().createObjectNode(),
            vedtaksperiodeId = UUID.randomUUID(),
            behandlingId = UUID.randomUUID(),
            aktørId = "aktør",
            behandlingskilde = BehandlingOpprettet.Behandlingskilde(OffsetDateTime.now(), OffsetDateTime.now(), BehandlingOpprettet.Avsender("SYKMELDT")),
            behandlingstype = BehandlingOpprettet.Behandlingstype("FØRSTEGANGSBEHANDLING")
        )

        val rad1behandling1 = leggTilBehandlingshendelse(behandlingId = behandlingId1, siste = true, versjon = Versjon.Companion.of("1.2.3"), hendelse = behandlingOpprettet) {
            it.put("behandlingtype", "FØRSTEGANGSBEHANDLING")
            it.put("uendretFelt", true)
        }

        migrer()
        assertKorrigert(rad1behandling1) { gammel, ny ->
            assertEquals("FØRSTEGANGSBEHANDLING", gammel.path("behandlingtype").asText())
            assertTrue(gammel.path("uendretFelt").asBoolean())
            assertEquals("SØKNAD", ny.path("behandlingtype").asText())
            assertTrue(ny.path("uendretFelt").asBoolean())
        }
    }
}