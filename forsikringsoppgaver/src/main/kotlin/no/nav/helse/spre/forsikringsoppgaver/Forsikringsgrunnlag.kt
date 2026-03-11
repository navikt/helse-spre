package no.nav.helse.spre.forsikringsoppgaver

import kotlinx.serialization.Serializable
import org.intellij.lang.annotations.Language

@Serializable
data class Forsikringsgrunnlag(
    val dekningsgrad: Int,
    val dag1Eller17: Int,
    val premiegrunnlag: String
)

fun Forsikringsgrunnlag.toJsonString(): String {
    @Language("JSON")
    return """{"dekningsgrad": $dekningsgrad, "dag1Eller17": $dag1Eller17, "premiegrunnlag": ${premiegrunnlag}}"""
}
