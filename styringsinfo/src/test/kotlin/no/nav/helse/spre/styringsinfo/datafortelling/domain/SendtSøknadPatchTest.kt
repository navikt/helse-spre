package no.nav.helse.spre.styringsinfo.datafortelling.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class SendtSøknadPatchTest {
    @Test
    fun `sjekker rekkefølge på enums som ikke må endres etter at respektive patcher er kjørt`() {
        SendtSøknadPatch.values().forEach {
            when (it) {
                SendtSøknadPatch.UPATCHET -> assertEquals(0, it.ordinal)
                SendtSøknadPatch.FNR -> assertEquals(1, it.ordinal)
                SendtSøknadPatch.ARBEIDSGIVER -> assertEquals(2, it.ordinal)
                SendtSøknadPatch.SPØRSMÅL -> assertEquals(3, it.ordinal)
            }
        }

    }
}