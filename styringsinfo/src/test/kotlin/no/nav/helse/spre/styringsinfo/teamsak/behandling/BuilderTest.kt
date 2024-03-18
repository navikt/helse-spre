package no.nav.helse.spre.styringsinfo.teamsak.behandling

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class BuilderTest {

    @Test
    fun `ignorerer funksjonelt like behandlinger`() {
        val forrige = lagBehandling()
        assertNull(Behandling.Builder(forrige).build(nå.plusDays(1), Behandling.Behandlingsmetode.MANUELL))
        val ny = Behandling.Builder(forrige).saksbehandlerEnhet("1234").build(nå.plusDays(1), Behandling.Behandlingsmetode.MANUELL)
        assertNotNull(ny)
        assertEquals("1234", ny!!.saksbehandlerEnhet)
    }

    @Test
    fun `får en feil om man prøver å legge til ny rad etter at noe er avsluttet`() {
        val forrige = lagBehandling().copy(behandlingsresultat = Behandling.Behandlingsresultat.INNVILGET, behandlingstatus = Behandling.Behandlingstatus.AVSLUTTET)
        assertThrows<IllegalStateException> { Behandling.Builder(forrige).saksbehandlerEnhet("1234").build(etterpå, Behandling.Behandlingsmetode.MANUELL) }
    }

    private val nå = LocalDateTime.now()
    private val etterpå = nå.plusDays(1)
    private fun lagBehandling() = Behandling(
        sakId = SakId(UUID.randomUUID()),
        behandlingId = BehandlingId(UUID.randomUUID()),
        relatertBehandlingId = null,
        aktørId = "1",
        mottattTid = nå,
        registrertTid = nå,
        funksjonellTid = nå,
        behandlingstatus = Behandling.Behandlingstatus.REGISTRERT,
        behandlingsmetode = Behandling.Behandlingsmetode.AUTOMATISK,
        behandlingskilde = Behandling.Behandlingskilde.SYKMELDT,
        periodetype = Behandling.Periodetype.FORLENGELSE,
        behandlingstype = Behandling.Behandlingstype.SØKNAD,
        behandlingsresultat = null,
        mottaker = null,
        saksbehandlerEnhet = null,
        beslutterEnhet = null
    )
}