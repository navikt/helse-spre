package no.nav.helse.spre.forsikringsoppgaver

import com.fasterxml.jackson.databind.JsonNode
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

fun JsonNode.asUuid(): UUID = UUID.fromString(asText())
fun JsonNode.asBigDecimal() = BigDecimal(asText())

/**
 * Beregner prosentvis avvik mellom sykepengegrunnlag og premiegrunnlag og returnerer true hvis avviket er større enn det akseptable avviket.
 *
 * @param sykepengegrunnlag Det beregnede sykepengegrunnlaget.
 * @param premiegrunnlag Det registrerte premiegrunnlaget.
 * @return true hvis avviket er større enn det akseptable avviket, ellers false.
 *
 * Formel: Avvik (%) = (|sykepengegrunnlag - premiegrunnlag|) / ((sykepengegrunnlag + premiegrunnlag) / 2) * 100
 */

fun String.toBigDecimal(scale: Int = 10): BigDecimal {
    return BigDecimal(this).setScale(scale, RoundingMode.HALF_UP)
}

fun beregnAvvik(sykepengegrunnlag: BigDecimal, premiegrunnlag: BigDecimal): BigDecimal {
    val differansen = (sykepengegrunnlag.subtract(premiegrunnlag)).abs()
    val gjennomsnittet = (sykepengegrunnlag.add(premiegrunnlag)).divide(BigDecimal("2"), RoundingMode.HALF_UP)
    return differansen.divide(gjennomsnittet, RoundingMode.HALF_UP).multiply(BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
}

