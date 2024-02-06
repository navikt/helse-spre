package no.nav.helse.spre.gosys.vedtakFattet

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.spre.gosys.objectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class VedtakFattetData(
    val id: UUID,
    val aktørId: String,
    val fødselsnummer: String,
    val vedtaksperiodeId: UUID,
    val opprettet: LocalDateTime,
    val sykepengegrunnlag: Double,
    val grunnlagForSykepengegrunnlag: Map<String, Double>,
    val fom: LocalDate,
    val tom: LocalDate,
    val skjæringstidspunkt: LocalDate,
    val utbetalingId: UUID?
) {
    companion object {
        fun fromJson(hendelseId: UUID, packet: JsonMessage) = VedtakFattetData(
            id = hendelseId,
            aktørId = packet["aktørId"].asText(),
            fødselsnummer = packet["fødselsnummer"].asText(),
            opprettet = packet["@opprettet"].asLocalDateTime(),
            vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText()),
            sykepengegrunnlag = packet["sykepengegrunnlag"].asDouble(),
            grunnlagForSykepengegrunnlag = objectMapper.readValue(packet["grunnlagForSykepengegrunnlagPerArbeidsgiver"].toString(), object: TypeReference<Map<String, Double>>() {}),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate(),
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
            sykepengegrunnlag = packet["sykepengegrunnlag"].asDouble(),
            grunnlagForSykepengegrunnlag = objectMapper.readValue(packet["grunnlagForSykepengegrunnlagPerArbeidsgiver"].toString(), object: TypeReference<Map<String, Double>>() {}),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate(),
            utbetalingId = packet["utbetalingId"].takeUnless(JsonNode::isMissingOrNull)?.let {
                UUID.fromString(it.asText())
            }
        )
    }
}
