package no.nav.helse.spre.gosys.feriepenger

import java.time.LocalDate
import java.time.LocalDateTime

data class FeriepengerPdfPayload(
    val tittel: String,
    val oppdrag: List<Oppdrag>,
    val utbetalt: LocalDateTime? // TODO: Ikke optional
)

data class Oppdrag(
    val type: OppdragType,
    val fom: LocalDate,
    val tom: LocalDate,
    val mottaker: String,
    val totalbeløp: Int
)

enum class OppdragType {
    ARBEIDSGIVER,
    PERSON
}

/*
Feks utbetalingsnotat i Gosys

Tittel: "Feriepenger utbetalt"

Beløp:

Mottaker:

dato:

(Periode: )

(kanskje støtte personbeløp og mtp neste år)

 */
