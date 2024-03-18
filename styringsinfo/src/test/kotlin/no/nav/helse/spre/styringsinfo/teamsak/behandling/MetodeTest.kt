package no.nav.helse.spre.styringsinfo.teamsak.behandling

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MetodeTest {

    @Test
    fun manuell() {
        assertEquals(MANUELL, MANUELL.neste(MANUELL))
        assertEquals(MANUELL, MANUELL.neste(AUTOMATISK))
        assertEquals(TOTRINNS, MANUELL.neste(TOTRINNS))
    }

    @Test
    fun automatisk() {
        assertEquals(MANUELL, AUTOMATISK.neste(MANUELL))
        assertEquals(AUTOMATISK, AUTOMATISK.neste(AUTOMATISK))
        assertEquals(TOTRINNS, AUTOMATISK.neste(TOTRINNS))
    }

    @Test
    fun totrinns() {
        assertEquals(TOTRINNS, TOTRINNS.neste(MANUELL))
        assertEquals(TOTRINNS, TOTRINNS.neste(AUTOMATISK))
        assertEquals(TOTRINNS, TOTRINNS.neste(TOTRINNS))
    }
}