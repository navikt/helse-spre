package no.nav.helse.spre.forsikringsoppgaver

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ForStortAvvikTest {

    @Test
    fun `for stort avvik`(
    ) {
        val sykepengegrunnlag = BigDecimal(400000)
        val premiegrunnlag = BigDecimal(200000)
        assertEquals(true, forStortAvvik(sykepengegrunnlag, premiegrunnlag))
    }

    @Test
    fun `ikke for stort avvik`(
    ) {
        val sykepengegrunnlag = BigDecimal(400000)
        val premiegrunnlag = BigDecimal(300000)
        assertEquals(false, forStortAvvik(sykepengegrunnlag, premiegrunnlag))
    }

    @ParameterizedTest
    @CsvSource(
        "400000, 200000, true",
        "200000, 400000, true",
        "300000, 400000, false",
        "400000, 300000, false"
    )
    fun `spiller ingen rolle vilket tal som er a eller b`(
        sykepengegrunnlag: String,
        premiegrunnlag: String,
        forventetResultat: Boolean
    ) {
        val sykepengegrunnlag = BigDecimal(sykepengegrunnlag)
        val premiegrunnlag = BigDecimal(premiegrunnlag)
        assertEquals(forventetResultat, forStortAvvik(sykepengegrunnlag, premiegrunnlag))
    }
}
