package no.nav.helse.spre.gosys.vedtak

import java.time.LocalDate

data class VedtakPdfPayload(
    val fødselsnummer: String,
    val fagsystemId: String,
    val type: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val linjer: List<Linje>,
    val organisasjonsnummer: String,
    val behandlingsdato: LocalDate,
    val dagerIgjen: Int,
    val automatiskBehandling: Boolean,
    val godkjentAv: String,
    val totaltTilUtbetaling: Int,
    val ikkeUtbetalteDager: List<IkkeUtbetalteDager>,
    val dagsats: Int?,
    val maksdato: LocalDate?,
    val sykepengegrunnlag: Double,
    val grunnlagForSykepengegrunnlag: Map<String, Double>
) {
    data class Linje(
        val fom: LocalDate,
        val tom: LocalDate,
        val grad: Int,
        val beløp: Int,
        val mottaker: String
    )

    data class IkkeUtbetalteDager(
        val fom: LocalDate,
        val tom: LocalDate,
        val grunn: String,
        val begrunnelser: List<String>
    )
}
