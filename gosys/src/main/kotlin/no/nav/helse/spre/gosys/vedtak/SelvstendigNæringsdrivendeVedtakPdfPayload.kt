package no.nav.helse.spre.gosys.vedtak

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class SNVedtakPdfPayload(
    val fødselsnummer: String,
    val type: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val linjer: List<VedtakPdfPayload.Linje>,
    val personOppdrag: VedtakPdfPayload.Oppdrag?,
    val arbeidsgiverOppdrag: VedtakPdfPayload.Oppdrag?,
    val behandlingsdato: LocalDate,
    val dagerIgjen: Int,
    val automatiskBehandling: Boolean,
    val godkjentAv: String,
    val sumNettoBeløp: Int,
    val sumTotalBeløp: Int,
    val ikkeUtbetalteDager: List<VedtakPdfPayload.IkkeUtbetalteDager>,
    val maksdato: LocalDate?,
    val sykepengegrunnlag: BigDecimal,
    val navn: String,
    val skjæringstidspunkt: LocalDate,
    val begrunnelser: Map<String, String>?,
    val beregningsgrunnlag: BigDecimal,
    val vedtakFattetTidspunkt: LocalDateTime,
    val pensjonsgivendeInntekter: List<PensjonsgivendeInntekt>,
)

data class PensjonsgivendeInntekt(
    val årstall: Int,
    val beløp: BigDecimal,
)
