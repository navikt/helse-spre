package no.nav.helse.spre.styringsinfo.teamsak.behandling

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

internal class BuilderTest {

    @Test
    fun `ignorerer funksjonelt like behandlinger`() {
        val forrige = lagBehandling()
        assertNull(Behandling.Builder(forrige).build(nå.plusDays(1), Behandling.Metode.AUTOMATISK))
        val ny = Behandling.Builder(forrige).saksbehandlerEnhet("1234").build(nå.plusDays(1), Behandling.Metode.AUTOMATISK)
        assertNotNull(ny)
        assertEquals("1234", ny!!.saksbehandlerEnhet)
    }

    @Test
    fun `får ikke en feil om man prøver å legge til ny rad etter at noe er avsluttet`() {
        val forrige = lagBehandling().copy(behandlingsresultat = Behandling.Behandlingsresultat.INNVILGET, behandlingstatus = Behandling.Behandlingstatus.AVSLUTTET)
        assertNull(Behandling.Builder(forrige).saksbehandlerEnhet("1234").build(etterpå, Behandling.Metode.MANUELL))
    }

    @Test
    fun behandlingsmetode() {
        val behandlingsmetodeAutomatisk = lagBehandling().copy(behandlingsmetode = Behandling.Metode.AUTOMATISK)
        assertEquals(Behandling.Metode.AUTOMATISK, Behandling.Builder(behandlingsmetodeAutomatisk).saksbehandlerEnhet("1234").build(etterpå, Behandling.Metode.AUTOMATISK)?.behandlingsmetode)
        assertEquals(Behandling.Metode.MANUELL, Behandling.Builder(behandlingsmetodeAutomatisk).saksbehandlerEnhet("1234").build(etterpå, Behandling.Metode.MANUELL)?.behandlingsmetode)
        assertEquals(Behandling.Metode.TOTRINNS, Behandling.Builder(behandlingsmetodeAutomatisk).saksbehandlerEnhet("1234").build(etterpå, Behandling.Metode.TOTRINNS)?.behandlingsmetode)

        val behandlingsmetodeManuell = lagBehandling().copy(behandlingsmetode = Behandling.Metode.MANUELL)
        assertEquals(Behandling.Metode.MANUELL, Behandling.Builder(behandlingsmetodeManuell).saksbehandlerEnhet("1234").build(etterpå, Behandling.Metode.AUTOMATISK)?.behandlingsmetode)
        assertEquals(Behandling.Metode.MANUELL, Behandling.Builder(behandlingsmetodeManuell).saksbehandlerEnhet("1234").build(etterpå, Behandling.Metode.MANUELL)?.behandlingsmetode)
        assertEquals(Behandling.Metode.TOTRINNS, Behandling.Builder(behandlingsmetodeManuell).saksbehandlerEnhet("1234").build(etterpå, Behandling.Metode.TOTRINNS)?.behandlingsmetode)

        val behandlingsmetodeTotrinns = lagBehandling().copy(behandlingsmetode = Behandling.Metode.TOTRINNS)
        assertEquals(Behandling.Metode.TOTRINNS, Behandling.Builder(behandlingsmetodeTotrinns).saksbehandlerEnhet("1234").build(etterpå, Behandling.Metode.AUTOMATISK)?.behandlingsmetode)
        assertEquals(Behandling.Metode.TOTRINNS, Behandling.Builder(behandlingsmetodeTotrinns).saksbehandlerEnhet("1234").build(etterpå, Behandling.Metode.MANUELL)?.behandlingsmetode)
        assertEquals(Behandling.Metode.TOTRINNS, Behandling.Builder(behandlingsmetodeTotrinns).saksbehandlerEnhet("1234").build(etterpå, Behandling.Metode.TOTRINNS)?.behandlingsmetode)
    }

    private val nå = OffsetDateTime.now()
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
        behandlingsmetode = Behandling.Metode.AUTOMATISK,
        behandlingskilde = Behandling.Behandlingskilde.SYKMELDT,
        periodetype = Behandling.Periodetype.FORLENGELSE,
        behandlingstype = Behandling.Behandlingstype.SØKNAD,
        behandlingsresultat = null,
        mottaker = null,
        saksbehandlerEnhet = null,
        beslutterEnhet = null,
        hendelsesmetode = Behandling.Metode.AUTOMATISK
    )
}