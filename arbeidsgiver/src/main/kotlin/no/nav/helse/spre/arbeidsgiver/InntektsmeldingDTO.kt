package no.nav.helse.spre.arbeidsgiver

import java.time.LocalDate
import java.time.LocalDateTime

internal class InntektsmeldingDTO private constructor(
    val type: Meldingstype,
    val organisasjonsnummer: String,
    val fødselsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val opprettet: LocalDateTime
){
    val meldingstype get() = type.name.toLowerCase().toByteArray()

    internal companion object {
        internal fun trengerInntektsmelding(
            organisasjonsnummer: String,
            fødselsnummer: String,
            fom: LocalDate,
            tom: LocalDate,
            opprettet: LocalDateTime
        ) = InntektsmeldingDTO(
            Meldingstype.TRENGER_INNTEKTSMELDING,
            organisasjonsnummer,
            fødselsnummer,
            fom,
            tom,
            opprettet
        )

        internal fun trengerIkkeInntektsmelding(
            organisasjonsnummer: String,
            fødselsnummer: String,
            fom: LocalDate,
            tom: LocalDate,
            opprettet: LocalDateTime
        ) = InntektsmeldingDTO(
            Meldingstype.TRENGER_IKKE_INNTEKTSMELDING,
            organisasjonsnummer,
            fødselsnummer,
            fom,
            tom,
            opprettet
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
