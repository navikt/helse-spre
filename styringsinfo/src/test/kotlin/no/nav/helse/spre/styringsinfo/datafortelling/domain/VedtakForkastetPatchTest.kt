package no.nav.helse.spre.styringsinfo.datafortelling.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class VedtakForkastetPatchTest {
    @Test
    fun `sjekker rekkefølge på enums som ikke må endres etter at respektive patcher er kjørt`() {
        VedtakForkastetPatch.values().forEach {
            when (it) {
                VedtakForkastetPatch.UPATCHET -> assertEquals(0, it.ordinal)
                VedtakForkastetPatch.FØDSELSNUMMER -> assertEquals(1, it.ordinal)
                VedtakForkastetPatch.ORGANISASJONSNUMMER -> assertEquals(2, it.ordinal)
            }
        }
    }
}