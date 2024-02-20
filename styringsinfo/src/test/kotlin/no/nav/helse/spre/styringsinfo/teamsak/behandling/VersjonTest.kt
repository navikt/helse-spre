package no.nav.helse.spre.styringsinfo.teamsak.behandling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class VersjonTest {
    @Test
    fun `kan ikke lag ugyldige versjoner`() {
        assertEquals("Ugyldig versjon tull", assertThrows<IllegalStateException> { Versjon.of("tull") }.message)
        assertEquals("Ugyldig major a.0.0", assertThrows<IllegalStateException> { Versjon.of("a.0.0") }.message)
        assertEquals("Ugyldig minor 0.a.0", assertThrows<IllegalStateException> { Versjon.of("0.a.0") }.message)
        assertEquals("Ugyldig patch 0.0.a", assertThrows<IllegalStateException> { Versjon.of("0.0.a") }.message)
        assertEquals("Ugyldig patch 0.0.-1", assertThrows<IllegalStateException> { Versjon.of("0.0.-1") }.message)
    }

    @Test
    fun `sammenligne versjoner`() {
        val versjoner = listOf(
            Versjon.of("0.0.1"),
            Versjon.of("0.1.0"),
            Versjon.of("1.0.0"),
            Versjon.of("2.0.0")
        )
        assertEquals(Versjon.of("2.0.0"), versjoner.max())
        assertEquals(Versjon.of("0.0.1"), versjoner.min())
    }

    @Test
    fun `Evaluerer versjon ut i fra felter`() {
        assertEquals(Versjon.of("0.0.4"), Versjon.of(initielleFelter))
        assertEquals(Versjon.of("0.1.0"), Versjon.of(initielleFelter + "saksbehandlerEnhet" + "beslutterEnhet"))

    }

    @Test
    fun `Feiler om versjon for felter ikke er definert`() {
        val initielleFelter = setOf("aktørId", "mottattTid", "registrertTid", "behandlingstatus", "behandlingtype", "behandlingskilde", "behandlingsmetode", "relatertBehandlingId", "behandlingsresultat")
        assertThrows<IllegalStateException> { Versjon.of(initielleFelter - "aktørId") }
        assertThrows<IllegalStateException> { Versjon.of(initielleFelter + "finnesIkke") }
        assertThrows<IllegalStateException> { Versjon.of(initielleFelter + "finnesIkke" - "aktørId") }
    }

    private val initielleFelter = setOf("aktørId", "mottattTid", "registrertTid", "behandlingstatus", "behandlingtype", "behandlingskilde", "behandlingsmetode", "relatertBehandlingId", "behandlingsresultat")
}