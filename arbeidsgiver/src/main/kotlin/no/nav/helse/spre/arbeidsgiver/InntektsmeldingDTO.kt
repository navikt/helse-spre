package no.nav.helse.spre.arbeidsgiver

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class InntektsmeldingDTO(
    val type: Meldingstype,
    val organisasjonsnummer: String,
    val fødselsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val opprettet: LocalDateTime
){
    val meldingstype get() = type.name.lowercase(Locale.getDefault()).toByteArray()

   internal companion object {
        internal fun JsonMessage.tilInntektsmeldingDTO(meldingstype: Meldingstype) = InntektsmeldingDTO(
            type = meldingstype,
            organisasjonsnummer = this["organisasjonsnummer"].asText(),
            fødselsnummer = this["fødselsnummer"].asText(),
            fom = this["fom"].asLocalDate(),
            tom = this["tom"].asLocalDate(),
            opprettet = this["@opprettet"].asLocalDateTime()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InntektsmeldingDTO

        if (type != other.type) return false
        if (organisasjonsnummer != other.organisasjonsnummer) return false
        if (fødselsnummer != other.fødselsnummer) return false
        if (fom != other.fom) return false
        if (tom != other.tom) return false
        if (opprettet != other.opprettet) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + organisasjonsnummer.hashCode()
        result = 31 * result + fødselsnummer.hashCode()
        result = 31 * result + fom.hashCode()
        result = 31 * result + tom.hashCode()
        result = 31 * result + opprettet.hashCode()
        return result
    }

}

internal enum class Meldingstype {
    TRENGER_INNTEKTSMELDING,
    TRENGER_IKKE_INNTEKTSMELDING
}
