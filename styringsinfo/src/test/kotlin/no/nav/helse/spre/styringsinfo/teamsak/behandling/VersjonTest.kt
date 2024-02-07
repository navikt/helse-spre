package no.nav.helse.spre.styringsinfo.teamsak.behandling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
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
    fun `Validerer om felter legges til eller fjernes`() {
        val førsteVersjon = Versjon.of("0.0.1")
        val førsteVersjonFelter = setOf("aktørId", "mottattTid", "registrertTid", "behandlingstatus", "behandlingtype", "behandlingskilde", "behandlingsmetode", "relatertBehandlingId", "behandlingsresultat")
        assertDoesNotThrow { førsteVersjon.valider(førsteVersjonFelter) }

        assertEquals(
            "Ettersom feltene [aktørId, mottattTid, registrertTid, behandlingstatus, behandlingtype, behandlingskilde, behandlingsmetode, relatertBehandlingId, behandlingsresultat] er fjernet burde versjon bumpes fra 0.0.1 til 1.0.0",
            assertThrows<IllegalStateException> { førsteVersjon.valider(emptySet()) }.message
        )

        assertEquals(
            "Ettersom feltene [aktørId, mottattTid, behandlingtype, behandlingskilde, behandlingsmetode, relatertBehandlingId, behandlingsresultat] er fjernet burde versjon bumpes fra 0.0.1 til 1.0.0",
            assertThrows<IllegalStateException> { førsteVersjon.valider(setOf("registrertTid", "behandlingstatus")) }.message
        )

        assertEquals(
            "Ettersom feltene [noeNytt] er lagt til burde versjon bumpes fra 0.0.1 til 0.1.0",
            assertThrows<IllegalStateException> { førsteVersjon.valider(førsteVersjonFelter + "noeNytt") }.message
        )
    }
}