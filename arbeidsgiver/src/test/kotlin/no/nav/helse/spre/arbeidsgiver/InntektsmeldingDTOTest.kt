package no.nav.helse.spre.arbeidsgiver

import no.nav.helse.spre.arbeidsgiver.Meldingstype.TRENGER_IKKE_INNTEKTSMELDING
import no.nav.helse.spre.arbeidsgiver.Meldingstype.TRENGER_INNTEKTSMELDING
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class InntektsmeldingDTOTest {

    companion object {
        private const val ORGNR = "123456789"
        private const val FNR = "12345678910"
        private val FOM = LocalDate.now().minusDays(1L)
        private val TOM = LocalDate.now()
        private val OPPRETTET = LocalDateTime.now()
    }

    @Test
    fun `oppretter inntektsmelding-melding når vi trenger inntektsmelding`() {
        val melding = InntektsmeldingDTO(TRENGER_INNTEKTSMELDING, ORGNR, FNR, FOM, TOM, OPPRETTET)
        assertEquals(TRENGER_INNTEKTSMELDING, melding.type)
        assertTrue(melding.meldingstype.contentEquals(TRENGER_INNTEKTSMELDING.name.lowercase(Locale.getDefault()).toByteArray()))
    }

    @Test
    fun `oppretter inntektsmelding-melding når vi ikke trenger inntektsmelding lenger`() {
        val melding = InntektsmeldingDTO(TRENGER_IKKE_INNTEKTSMELDING, ORGNR, FNR, FOM, TOM, OPPRETTET)
        assertEquals(TRENGER_IKKE_INNTEKTSMELDING, melding.type)
        assertTrue(melding.meldingstype.contentEquals(TRENGER_IKKE_INNTEKTSMELDING.name.lowercase(Locale.getDefault()).toByteArray()))
    }
}
