package no.nav.helse.spre.gosys.annullering

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

data class TomAnnulleringMessage(
    val hendelseId: UUID,
    val vedtaksperiodeId: UUID,
    val fødselsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val organisasjonsnummer: String,
    val opprettet: LocalDateTime
) {
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val norskFom: String = fom.format(formatter)
    val norskTom: String = tom.format(formatter)

    internal fun toPdfPayload(organisasjonsnavn: String?, navn: String?) = TomAnnulleringPdfPayload(
        fødselsnummer = fødselsnummer,
        fom = fom,
        tom = tom,
        organisasjonsnummer = organisasjonsnummer,
        dato = opprettet,
        epost = "tbd@nav.no",
        ident = "SPEIL",
        personFagsystemId = null,
        arbeidsgiverFagsystemId = null,
        organisasjonsnavn = organisasjonsnavn,
        navn = navn
    )

    data class TomAnnulleringPdfPayload(
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
    )
}
