package no.nav.helse.spre.gosys.annullering

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class AnnulleringMessage private constructor(
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

    constructor(hendelseId: UUID, packet: JsonMessage) : this(
        hendelseId = hendelseId,
        utbetalingId = UUID.fromString(packet["utbetalingId"].asText()),
        fødselsnummer = packet["fødselsnummer"].asText(),
        fom = packet["fom"].asLocalDate(),
        tom = packet["tom"].asLocalDate(),
        organisasjonsnummer = packet["organisasjonsnummer"].asText(),
        dato = packet["tidspunkt"].asLocalDateTime(),
        saksbehandlerIdent = packet["ident"].asText(),
        saksbehandlerEpost = packet["epost"].asText(),
        personFagsystemId = packet["personFagsystemId"].takeUnless { it.isMissingOrNull() }?.asText(),
        arbeidsgiverFagsystemId = packet["arbeidsgiverFagsystemId"].takeUnless { it.isMissingOrNull() }?.asText()
    )

    internal fun toPdfPayloadV2(organisasjonsnavn: String?, navn: String?) = AnnulleringPdfPayloadV2(
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
