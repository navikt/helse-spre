package no.nav.helse.spre.gosys.annullering

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class AnnulleringMessage private constructor(
    val hendelseId: UUID,
    val fødselsnummer: String,
    val aktørId: String,
    private val fom: LocalDate,
    private val tom: LocalDate,
    private val organisasjonsnummer: String,
    private val fagsystemId: String,
    private val saksbehandlerId: String,
    private val dato: LocalDateTime,
    private val linjer: List<Linje>
) {
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val norskFom: String = fom.format(formatter)
    val norskTom: String = tom.format(formatter)

    constructor(packet: JsonMessage) :
        this(
            hendelseId = UUID.fromString(packet["@id"].asText()),
            fødselsnummer = packet["fødselsnummer"].asText(),
            aktørId = packet["aktørId"].asText(),
            organisasjonsnummer = packet["organisasjonsnummer"].asText(),
            fagsystemId = packet["fagsystemId"].asText(),
            fom = requireNotNull(packet.utbetalingslinjer().map { it.fom }.minOrNull()),
            tom = requireNotNull(packet.utbetalingslinjer().map { it.tom }.maxOrNull()),
            saksbehandlerId = packet["saksbehandlerEpost"].asText(),
            dato = packet["annullertAvSaksbehandler"].asLocalDateTime(),
            linjer = packet.utbetalingslinjer()
        )

    internal fun toPdfPayload() = AnnulleringPdfPayload(
        fødselsnummer = fødselsnummer,
        fagsystemId = fagsystemId,
        fom = fom,
        tom = tom,
        organisasjonsnummer = organisasjonsnummer,
        saksbehandlerId = saksbehandlerId,
        dato = dato,
        linjer = linjer.map {
            AnnulleringPdfPayload.Linje(
                fom = it.fom,
                tom = it.tom,
                grad = it.grad,
                beløp = it.beløp
            )
        }
    )

    data class Linje(
        val fom: LocalDate,
        val tom: LocalDate,
        val grad: Int,
        val beløp: Int
    )
}

private fun JsonMessage.utbetalingslinjer() = this["utbetalingslinjer"].map {
    AnnulleringMessage.Linje(
        fom = it["fom"].asLocalDate(),
        tom = it["tom"].asLocalDate(),
        grad = it["grad"].asInt(),
        beløp = it["beløp"].asInt()
    )
}
