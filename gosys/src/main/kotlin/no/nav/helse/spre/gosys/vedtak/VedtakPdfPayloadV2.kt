package no.nav.helse.spre.gosys.vedtak

import java.time.LocalDate

data class VedtakPdfPayloadV2(
    val fødselsnummer: String,
    val type: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val linjer: List<Linje>,
    val personOppdrag: Oppdrag,
    val arbeidsgiverOppdrag: Oppdrag,
    val organisasjonsnummer: String,
    val behandlingsdato: LocalDate,
    val dagerIgjen: Int,
    val automatiskBehandling: Boolean,
    val godkjentAv: String,
    val sumNettoBeløp: Int,
    val ikkeUtbetalteDager: List<IkkeUtbetalteDager>,
    val maksdato: LocalDate?,
    val sykepengegrunnlag: Double,
    val grunnlagForSykepengegrunnlag: Map<String, Double>
) {
    data class Oppdrag(
        val fagsystemId: String
    )

    enum class MottakerType {
        Arbeidsgiver {
            override fun formatter(mottaker: String): String = mottaker.chunked(3).joinToString(separator = " ")
        },
        Person {
            override fun formatter(mottaker: String): String = mottaker.chunked(6).joinToString(separator = " ")
        };

        abstract fun formatter(mottaker: String): String
    }

    data class Linje(
        val fom: LocalDate,
        val tom: LocalDate,
        val grad: Int,
        val dagsats: Int,
        val mottaker: String,
        val mottakerType: MottakerType = MottakerType.Arbeidsgiver,
        val totalbeløp: Int
    )

    data class IkkeUtbetalteDager(
        val fom: LocalDate,
        val tom: LocalDate,
        val grunn: String,
        val begrunnelser: List<String>
    )
}


fun List<VedtakPdfPayloadV2.Linje>.slåSammen(other: List<VedtakPdfPayloadV2.Linje>): List<VedtakPdfPayloadV2.Linje> {
    return (this + other)
        .sortedBy { it.mottakerType }
        .sortedByDescending { it.fom }
}