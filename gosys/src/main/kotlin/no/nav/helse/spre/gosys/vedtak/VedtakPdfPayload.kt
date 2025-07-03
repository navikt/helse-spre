package no.nav.helse.spre.gosys.vedtak

import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.helse.spre.gosys.vedtakFattet.ArbeidsgiverData

data class VedtakPdfPayload(
    val fødselsnummer: String,
    val type: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val linjer: List<Linje>,
    val personOppdrag: Oppdrag?,
    val arbeidsgiverOppdrag: Oppdrag?,
    val organisasjonsnummer: String,
    val behandlingsdato: LocalDate,
    val dagerIgjen: Int,
    val automatiskBehandling: Boolean,
    val godkjentAv: String,
    val sumNettoBeløp: Int,
    val sumTotalBeløp: Int,
    val ikkeUtbetalteDager: List<IkkeUtbetalteDager>,
    val maksdato: LocalDate?,
    val sykepengegrunnlag: Double,
    val navn: String,
    val organisasjonsnavn: String,
    val skjæringstidspunkt: LocalDate,
    val avviksprosent: Double?,
    val skjønnsfastsettingtype: String?,
    val skjønnsfastsettingårsak: String?,
    val arbeidsgivere: List<ArbeidsgiverData>?,
    val begrunnelser: Map<String, String>?,
    val vedtakFattetTidspunkt: LocalDateTime,
) {
    data class Oppdrag(
        val fagsystemId: String
    )

    enum class MottakerType {
        Arbeidsgiver, Person
    }

    data class Linje(
        val fom: LocalDate,
        val tom: LocalDate,
        val grad: Int,
        val dagsats: Int,
        val mottaker: String,
        val mottakerType: MottakerType = MottakerType.Arbeidsgiver,
        val totalbeløp: Int,
        val erOpphørt: Boolean
    )

    data class IkkeUtbetalteDager(
        val fom: LocalDate,
        val tom: LocalDate,
        val begrunnelser: List<String>
    ) {
        init {
            check(begrunnelser.isNotEmpty()) {
                "Forventer minst én begrunnelse for en ikke-utbetalt dag"
            }
        }
    }
}


fun List<VedtakPdfPayload.Linje>.slåSammen(other: List<VedtakPdfPayload.Linje>): List<VedtakPdfPayload.Linje> {
    return (this + other)
        .sortedBy { it.mottakerType }
        .sortedByDescending { it.fom }
}
