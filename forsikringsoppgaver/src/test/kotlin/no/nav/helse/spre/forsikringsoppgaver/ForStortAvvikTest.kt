package no.nav.helse.spre.forsikringsoppgaver

import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ForStortAvvikTest {

    @Test
    fun `for stort avvik`(
    ) {
        val sykepengegrunnlag = "400000".toBigDecimal()
        val premiegrunnlag = "200000".toBigDecimal()
        assertEquals("66.67".toBigDecimal(2), beregnAvvik(sykepengegrunnlag, premiegrunnlag))
    }

    @Test
    fun `ikke for stort avvik`(
    ) {
        val sykepengegrunnlag = "400000".toBigDecimal()
        val premiegrunnlag = "300000".toBigDecimal()
        assertEquals("28.57".toBigDecimal(2), beregnAvvik(sykepengegrunnlag, premiegrunnlag))
    }

    @ParameterizedTest
    @CsvSource(
        "400000, 200000, 66.67",
        "200000, 400000, 66.67",
        "300000, 400000, 28.57",
        "400000, 300000, 28.57"
    )
    fun `spiller ingen rolle vilket tal som er a eller b`(
        sykepengegrunnlag: String,
        premiegrunnlag: String,
        forventetResultat: String
    ) {
        val sykepengegrunnlag = sykepengegrunnlag.toBigDecimal()
        val premiegrunnlag = premiegrunnlag.toBigDecimal()
        assertEquals(forventetResultat.toBigDecimal(2), beregnAvvik(sykepengegrunnlag, premiegrunnlag))
    }
}
