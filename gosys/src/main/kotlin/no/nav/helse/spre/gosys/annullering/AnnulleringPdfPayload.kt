package no.nav.helse.spre.gosys.annullering

import java.time.LocalDate
import java.time.LocalDateTime

data class AnnulleringPdfPayload(
    val fødselsnummer: String,
    val fagsystemId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val organisasjonsnummer: String,
    val saksbehandlerId: String,
    val dato: LocalDateTime,
    val linjer: List<Linje>
) {
    data class Linje(
        val fom: LocalDate,
        val tom: LocalDate,
        val grad: Int,
        val beløp: Int
    )
}

data class AnnulleringPdfPayloadV2(
    val fødselsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val epost: String,
    val ident: String,
    val personFagsystemId: String?,
    val arbeidsgiverFagsystemId: String?,
    val organisasjonsnummer: String,
    val dato: LocalDateTime,
    val organisasjonsnavn: String?,
    val navn: String?
) {
    init {
        require(arbeidsgiverFagsystemId != null || personFagsystemId != null) { "En annullering må inneholde en fagsystemId" }
    }
}
