package no.nav.helse.spre.gosys.vedtak

import no.nav.helse.spre.testhelpers.januar
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

internal class VedtakPdfPayloadV2KtTest {

    private val tomListe: List<VedtakPdfPayloadV2.Linje> = emptyList()

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

    @Test
    fun `kronologisk stigende rekkefølge innenfor en arbeidsgiver`() {
        val arbeidsgiverLinjer = listOf(
            arbeidsgiverlinje(5.januar, 9.januar),
            arbeidsgiverlinje(10.januar, 14.januar),
            arbeidsgiverlinje(15.januar, 20.januar)
        )

        assertEquals(
            listOf(
                arbeidsgiverlinje(15.januar, 20.januar),
                arbeidsgiverlinje(10.januar, 14.januar),
                arbeidsgiverlinje(5.januar, 9.januar)
            ), arbeidsgiverLinjer.slåSammen(tomListe)
        )
    }

    @Test
    fun `kronologisk stigende rekkefølge på arbeidsgiver og personlinjer`() {
        val arbeidsgiverLinjer = listOf(
            arbeidsgiverlinje(15.januar, 20.januar),
            arbeidsgiverlinje(5.januar, 9.januar),
            arbeidsgiverlinje(10.januar, 14.januar)
        )
        val personLinjer = listOf(
            personlinje(11.januar, 16.januar),
            personlinje(1.januar, 6.januar)
        )

        assertEquals(
            listOf(
                arbeidsgiverlinje(15.januar, 20.januar),
                personlinje(11.januar, 16.januar),
                arbeidsgiverlinje(10.januar, 14.januar),
                arbeidsgiverlinje(5.januar, 9.januar),
                personlinje(1.januar, 6.januar)
            ), arbeidsgiverLinjer.slåSammen(personLinjer)
        )
    }

    @Test
    fun `arbeidsgiver først, når arbeidsgiver og personlinje med lik fom`() {
        val arbeidsgiverLinjer = listOf(
            arbeidsgiverlinje(15.januar, 20.januar),
        )
        val personLinjer = listOf(
            personlinje(15.januar, 16.januar),
        )

        assertEquals(
            listOf(
                arbeidsgiverlinje(15.januar, 20.januar),
                personlinje(15.januar, 16.januar)
            ), personLinjer.slåSammen(arbeidsgiverLinjer)
        )
    }

    fun arbeidsgiverlinje(
        fom: LocalDate = 17.januar,
        tom: LocalDate = 31.januar,
        grad: Int = 100,
        dagsats: Int = 1400,
        mottaker: String = "123 456 789",
        totalbeløp: Int = 20000
    ) = linje(fom, tom, grad, dagsats, mottaker, VedtakPdfPayloadV2.MottakerType.Arbeidsgiver, totalbeløp)

    fun personlinje(
        fom: LocalDate = 17.januar,
        tom: LocalDate = 31.januar,
        grad: Int = 100,
        dagsats: Int = 1400,
        mottaker: String = "123456 78999",
        totalbeløp: Int = 20000
    ) = linje(fom, tom, grad, dagsats, mottaker, VedtakPdfPayloadV2.MottakerType.Person, totalbeløp)

    fun linje(
        fom: LocalDate,
        tom: LocalDate,
        grad: Int,
        dagsats: Int,
        mottaker: String,
        mottakerType: VedtakPdfPayloadV2.MottakerType,
        totalbeløp: Int
    ) =
        VedtakPdfPayloadV2.Linje(
            fom = fom,
            tom = tom,
            grad = grad,
            dagsats = dagsats,
            mottaker = mottaker,
            mottakerType = mottakerType,
            totalbeløp = totalbeløp
        )
}