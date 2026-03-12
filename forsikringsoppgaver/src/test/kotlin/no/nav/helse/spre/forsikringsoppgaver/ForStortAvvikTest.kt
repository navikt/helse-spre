package no.nav.helse.spre.forsikringsoppgaver

import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ForStortAvvikTest {

    @Test
    fun `intet avvik`(
    ) {
        val sykepengegrunnlag = "400000".toBigDecimal()
        val premiegrunnlag = "400000".toBigDecimal()
        assertEquals("0.00".toBigDecimal(2), beregnAvvik(sykepengegrunnlag, premiegrunnlag))
    }

    @ParameterizedTest
    @CsvSource(
        "400000, 200000, 50.00",
        "200000, 400000, 100.00",
        "300000, 400000, 33.33",
        "400000, 300000, 25.00"
    )
    fun `avvik blir som forventet`(
        sykepengegrunnlag: String,
        premiegrunnlag: String,
        forventetResultat: String
    ) {
        val sykepengegrunnlag = sykepengegrunnlag.toBigDecimal()
        val premiegrunnlag = premiegrunnlag.toBigDecimal()
        assertEquals(forventetResultat.toBigDecimal(2), beregnAvvik(sykepengegrunnlag, premiegrunnlag))
    }
}
