package no.nav.helse.spre.gosys.feriepenger

import java.time.LocalDate
import java.time.LocalDateTime

data class FeriepengerPdfPayload(
    val tittel: String,
    val oppdrag: List<OppdragPdfPayload>,
    val utbetalt: LocalDateTime,
    val orgnummer: String,
    val fødselsnummer: String
)

data class OppdragPdfPayload(
    val type: OppdragType,
    val fom: LocalDate,
    val tom: LocalDate,
    val mottaker: String,
    val totalbeløp: Int,
    val fagsystemId: String
)

enum class OppdragType {
    ARBEIDSGIVER,
    PERSON
}
