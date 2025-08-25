package no.nav.helse.spre.gosys.feriepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import java.time.LocalDateTime
import java.util.*

class FeriepengerMessage(val hendelseId: UUID, packet: JsonMessage) {
    val fødselsnummer = packet["fødselsnummer"].asText()
    val orgnummer: String = packet["organisasjonsnummer"].asText()
    val fom = packet["fom"].asLocalDate()
    val tom = packet["tom"].asLocalDate()
    val utbetalt: LocalDateTime = packet["@opprettet"].asLocalDateTime()
    private val arbeidsgiveroppdrag = OppdragPdfPayload(
        type = OppdragType.ARBEIDSGIVER,
        fagsystemId = packet["arbeidsgiverOppdrag.fagsystemId"].asText(),
        fom = fom,
        tom = tom,
        mottaker = packet["arbeidsgiverOppdrag.mottaker"].asText(),
        totalbeløp = packet["arbeidsgiverOppdrag.totalbeløp"].asInt()
    )

    private val personoppdrag = OppdragPdfPayload(
        type = OppdragType.PERSON,
        fagsystemId = packet["personOppdrag.fagsystemId"].asText(),
        fom = fom,
        tom = tom,
        mottaker = packet["personOppdrag.mottaker"].asText(),
        totalbeløp = packet["personOppdrag.totalbeløp"].asInt()
    )

    val oppdrag = listOfNotNull(arbeidsgiveroppdrag, personoppdrag)

    init {
        check(oppdrag.isNotEmpty()) {
            "forventer minst ett oppdrag"
        }
    }
}
