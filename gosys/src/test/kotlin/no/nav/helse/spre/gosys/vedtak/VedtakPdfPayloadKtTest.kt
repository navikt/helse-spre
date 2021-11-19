package no.nav.helse.spre.gosys.vedtak

import no.nav.helse.spre.testhelpers.januar
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

internal class VedtakPdfPayloadKtTest {

    private val tomListe: List<VedtakPdfPayload.Linje> = emptyList()

    @Test
    fun `slå sammen to tomme linjer`() {
        assertEquals(0, tomListe.slåSammen(emptyList()).size)
    }

    @Test
    fun `slå sammen liste med tom linjer`() {
        val arbeidsgiverLinjer = listOf(arbeidsgiverlinje())

        assertEquals(arbeidsgiverLinjer, tomListe.slåSammen(arbeidsgiverLinjer))
        assertEquals(arbeidsgiverLinjer, arbeidsgiverLinjer.slåSammen(tomListe))
    }

    fun arbeidsgiverlinje(
        fom: LocalDate = 17.januar,
        tom: LocalDate = 31.januar,
        grad: Int = 100,
        beløp: Int = 1400,
        mottaker: String = "123 456 789"
    ) = linje(fom, tom, grad, beløp, mottaker)

    fun personlinje(
        fom: LocalDate = 17.januar,
        tom: LocalDate = 31.januar,
        grad: Int = 100,
        beløp: Int = 1400,
        mottaker: String = "123456 78999"
    ) = linje(fom, tom, grad, beløp, mottaker)

    fun linje(
        fom: LocalDate,
        tom: LocalDate,
        grad: Int,
        beløp: Int,
        mottaker: String
    ) =
        VedtakPdfPayload.Linje(
            fom = fom,
            tom = tom,
            grad, beløp, mottaker
        )
}