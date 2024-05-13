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
    val utbetalingId: UUID?,
    val sykepengegrunnlagsfakta: SykepengegrunnlagsfaktaData,
    val begrunnelser: List<Begrunnelse>?,
) {
    companion object {
        fun fromJson(hendelseId: UUID, packet: JsonMessage) = VedtakFattetData(
            id = hendelseId,
            aktørId = packet["aktørId"].asText(),
            fødselsnummer = packet["fødselsnummer"].asText(),
            opprettet = packet["@opprettet"].asLocalDateTime(),
            vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText()),
            sykepengegrunnlag = packet["sykepengegrunnlag"].asDouble(),
            grunnlagForSykepengegrunnlag = objectMapper.readValue(
                packet["grunnlagForSykepengegrunnlagPerArbeidsgiver"].toString(),
                object : TypeReference<Map<String, Double>>() {}),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate(),
            utbetalingId = packet["utbetalingId"].takeUnless(JsonNode::isMissingOrNull)?.let {
                UUID.fromString(it.asText())
            },
            sykepengegrunnlagsfakta = sykepengegrunnlagsfakta(json = packet["sykepengegrunnlagsfakta"]),
            begrunnelser = packet["begrunnelser"].takeUnless { it.isMissingOrNull() }?.let { begrunnelser(json = it) },
        )

        fun fromJson(packet: JsonNode) = VedtakFattetData(
            id = packet["@id"].asText().let { UUID.fromString(it) },
            aktørId = packet["aktørId"].asText(),
            fødselsnummer = packet["fødselsnummer"].asText(),
            opprettet = packet["@opprettet"].asLocalDateTime(),
            vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText()),
            sykepengegrunnlag = packet["sykepengegrunnlag"].asDouble(),
            grunnlagForSykepengegrunnlag = objectMapper.readValue(
                packet["grunnlagForSykepengegrunnlagPerArbeidsgiver"].toString(),
                object : TypeReference<Map<String, Double>>() {}),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate(),
            utbetalingId = packet["utbetalingId"]?.let { UUID.fromString(it.asText()) },
            sykepengegrunnlagsfakta = sykepengegrunnlagsfakta(json = packet["sykepengegrunnlagsfakta"]),
            begrunnelser = packet["begrunnelser"]?.let { begrunnelser(json = it) },
        )

        private fun begrunnelser(json: JsonNode): List<Begrunnelse> = json.map { begrunnelse ->
            Begrunnelse(
                type = begrunnelse["type"].asText(),
                begrunnelse = begrunnelse["begrunnelse"].asText(),
                perioder = begrunnelse["perioder"].map {
                    Periode(
                        fom = it["fom"].asLocalDate(),
                        tom = it["tom"].asLocalDate()
                    )
                }
            ) }

        private fun sykepengegrunnlagsfakta(json: JsonNode): SykepengegrunnlagsfaktaData = SykepengegrunnlagsfaktaData(
            omregnetÅrsinntekt = json["omregnetÅrsinntekt"].asDouble(),
            fastsatt = json["fastsatt"].asText(),
            innrapportertÅrsinntekt = json["innrapportertÅrsinntekt"]?.asDouble(),
            avviksprosent = json["avviksprosent"]?.asDouble(),
            seksG = json["innrapportertÅrsinntekt"]?.asDouble(),
            tags = json["tags"]?.map { it.asText() },
            skjønnsfastsettingtype = json["skjønnsfastsettingtype"]?.let { enumValueOf<Skjønnsfastsettingtype>(it.asText()) },
            skjønnsfastsettingårsak = json["skjønnsfastsettingårsak"]?.let { enumValueOf<Skjønnsfastsettingårsak>(it.asText()) },
            skjønnsfastsatt = json["skjønnsfastsatt"]?.asDouble(),
            arbeidsgivere = json["arbeidsgivere"]?.map { arbeidsgiver ->
                ArbeidsgiverData(
                    organisasjonsnummer = arbeidsgiver["arbeidsgiver"].asText(),
                    omregnetÅrsinntekt = arbeidsgiver["omregnetÅrsinntekt"].asDouble(),
                    innrapportertÅrsinntekt = arbeidsgiver["innrapportertÅrsinntekt"].asDouble(),
                    skjønnsfastsatt = arbeidsgiver["skjønnsfastsatt"]?.asDouble()
                )
            },
        )
    }
}

data class Begrunnelse(
    val type: String,
    val begrunnelse: String,
    val perioder: List<Periode>
)

data class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
)

data class SykepengegrunnlagsfaktaData(
    val omregnetÅrsinntekt: Double,
    val fastsatt: String,
    val innrapportertÅrsinntekt: Double?,
    val avviksprosent: Double?,
    val seksG: Double?,
    val tags: List<String>?,
    val skjønnsfastsettingtype: Skjønnsfastsettingtype?,
    val skjønnsfastsettingårsak: Skjønnsfastsettingårsak?,
    val skjønnsfastsatt: Double?,
    val arbeidsgivere: List<ArbeidsgiverData>?,
)

data class ArbeidsgiverData(
    val organisasjonsnummer: String,
    val omregnetÅrsinntekt: Double,
    val innrapportertÅrsinntekt: Double,
    val skjønnsfastsatt: Double?,
)

enum class Skjønnsfastsettingtype {
    OMREGNET_ÅRSINNTEKT,
    RAPPORTERT_ÅRSINNTEKT,
    ANNET,
}

enum class Skjønnsfastsettingårsak {
    ANDRE_AVSNITT,
    TREDJE_AVSNITT
}
