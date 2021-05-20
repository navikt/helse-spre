package no.nav.helse.spre.gosys.vedtakFattet

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.isMissingOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class VedtakFattetData(
    val id: UUID,
    val aktørId: String,
    val fødselsnummer: String,
    val vedtaksperiodeId: UUID,
    val opprettet: LocalDateTime,
    val erAvsluttetUtenGodkjenning: Boolean,
    val sykepengegrunnlag: Double,
    val fom: LocalDate,
    val tom: LocalDate,
    val utbetalingId: UUID?
) {
    companion object {
        fun fromJson(packet: JsonMessage) = VedtakFattetData(
            id = packet["@id"].asText().let{UUID.fromString(it)},
            aktørId = packet["aktørId"].asText(),
            fødselsnummer = packet["fødselsnummer"].asText(),
            opprettet = packet["@opprettet"].asLocalDateTime(),
            vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText()),
            erAvsluttetUtenGodkjenning = packet["@forårsaket_av.event_name"].asText() == "sendt_søknad_arbeidsgiver",
            sykepengegrunnlag = packet["sykepengegrunnlag"].asDouble(),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            utbetalingId = packet["utbetalingId"].takeUnless(JsonNode::isMissingOrNull)?.let {
                UUID.fromString(it.asText())
            }
        )

        fun fromJson(packet: JsonNode) = VedtakFattetData(
            id = packet["@id"].asText().let{UUID.fromString(it)},
            aktørId = packet["aktørId"].asText(),
            fødselsnummer = packet["fødselsnummer"].asText(),
            opprettet = packet["@opprettet"].asLocalDateTime(),
            vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText()),
            erAvsluttetUtenGodkjenning = packet["@forårsaket_av"]["event_name"].asText() == "sendt_søknad_arbeidsgiver",
            sykepengegrunnlag = packet["sykepengegrunnlag"].asDouble(),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            utbetalingId = packet["utbetalingId"].takeUnless(JsonNode::isMissingOrNull)?.let {
                UUID.fromString(it.asText())
            }
        )
    }
}
