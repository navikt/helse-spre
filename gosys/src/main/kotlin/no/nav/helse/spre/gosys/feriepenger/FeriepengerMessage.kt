package no.nav.helse.spre.gosys.feriepenger

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class FeriepengerMessage(packet: JsonMessage) {
    val hendelseId = UUID.fromString(packet["@id"].asText())
    val fødselsnummer = packet["fødselsnummer"].asText()
    val aktørId = packet["aktørId"].asText()
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    // TODO: Kommer feriepenger til å lage flere linjer?
    val norskFom: String = packet["arbeidsgiverOppdrag.linjer"].single()["fom"].asLocalDate().format(formatter)
    val norskTom: String = packet["arbeidsgiverOppdrag.linjer"].single()["tom"].asLocalDate().format(formatter)
}
