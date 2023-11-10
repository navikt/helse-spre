package no.nav.helse.spre.styringsinfo.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class VedtakFattetPatchTest {
    @Test
    fun `sjekker rekkefølge på enums som ikke må endres etter at respektive patcher er kjørt`() {
        VedtakFattetPatch.values().forEach {
            when (it) {
                VedtakFattetPatch.UPATCHET -> assertEquals(0, it.ordinal)
                VedtakFattetPatch.FØDSELSNUMMER -> assertEquals(1, it.ordinal)
                VedtakFattetPatch.ORGANISASJONSNUMMER -> assertEquals(2, it.ordinal)
            }
        }
    }
}