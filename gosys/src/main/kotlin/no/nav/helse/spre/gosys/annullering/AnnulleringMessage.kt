package no.nav.helse.spre.gosys.annullering

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

data class AnnulleringMessage(
    val hendelseId: UUID,
    val utbetalingId: UUID,
    val fødselsnummer: String,
    private val fom: LocalDate,
    private val tom: LocalDate,
    val organisasjonsnummer: String,
    private val dato: LocalDateTime,
    private val saksbehandlerIdent: String,
    private val saksbehandlerEpost: String,
    private val personFagsystemId: String?,
    private val arbeidsgiverFagsystemId: String?,
) {
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val norskFom: String = fom.format(formatter)
    val norskTom: String = tom.format(formatter)

    internal fun toPdfPayload(organisasjonsnavn: String?, navn: String?) = AnnulleringPdfPayload(
        fødselsnummer = fødselsnummer,
        fom = fom,
        tom = tom,
        organisasjonsnummer = organisasjonsnummer,
        dato = dato,
        epost = saksbehandlerEpost,
        ident = saksbehandlerIdent,
        personFagsystemId = personFagsystemId,
        arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
        organisasjonsnavn = organisasjonsnavn,
        navn = navn
    )
}
