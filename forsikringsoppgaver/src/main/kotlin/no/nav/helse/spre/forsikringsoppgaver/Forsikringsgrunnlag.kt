package no.nav.helse.spre.forsikringsoppgaver

import java.math.BigDecimal

data class Forsikringsgrunnlag(
    val dekningsgrad: Int,
    val dag1Eller17: Int,
    val premiegrunnlag: String?
)
