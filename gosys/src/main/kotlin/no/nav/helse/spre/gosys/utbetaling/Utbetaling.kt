package no.nav.helse.spre.gosys.utbetaling

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.spre.gosys.objectMapper
import no.nav.helse.spre.gosys.sikkerLogg
import no.nav.helse.spre.gosys.vedtak.VedtakMediator
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayloadV2
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao

data class Utbetaling(
    val utbetalingId: UUID,
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int,
    val maksdato: LocalDate,
    val automatiskBehandling: Boolean,
    val arbeidsgiverOppdrag: OppdragDto,
    val personOppdrag: OppdragDto,
    val type: Utbetalingtype,
    val ident: String,
    val epost: String,
    val opprettet: LocalDateTime,
    private val utbetalingsdager: List<UtbetalingdagDto>
) {

    val ikkeUtbetalingsdager = utbetalingsdager.filter { it.type in IkkeUtbetalingsdagtyper }

    internal fun søknadsperiode(vedtaksperiode: Pair<LocalDate, LocalDate>): Pair<LocalDate, LocalDate> {
        val dager = utbetalingsdager.filter { it.dato >= vedtaksperiode.first && it.dato <= vedtaksperiode.second }

        val fom = when (dager.none { it.type == "ArbeidIkkeGjenopptattDag" }) {
            true -> vedtaksperiode.first
            false -> dager.firstOrNull { it.type != "ArbeidIkkeGjenopptattDag" }?.dato ?: vedtaksperiode.first
        }

        return fom to vedtaksperiode.second
    }

    private fun vedtakOrNull(vedtakFattetDao: VedtakFattetDao) = vedtakFattetDao.finnVedtakFattetData(utbetalingId).let { vedtak ->
        if (vedtak.size > 1) sikkerLogg.warn("${vedtak.map { "Vedtak ${it.id} for Vedtaksperiode ${it.vedtaksperiodeId}"}} peker alle på Utbetalingen $utbetalingId")
        vedtak.singleOrNullOrThrow()
    }

    internal fun avgjørVidereBehandling(vedtakFattetDao: VedtakFattetDao, vedtakMediator: VedtakMediator) {
        vedtakOrNull(vedtakFattetDao)?.let { vedtaksperiode ->
            vedtakMediator.opprettSammenslåttVedtak(
                vedtaksperiode.fom,
                vedtaksperiode.tom,
                vedtaksperiode.sykepengegrunnlag,
                vedtaksperiode.grunnlagForSykepengegrunnlag,
                vedtaksperiode.skjæringstidspunkt,
                this
            )
        }
    }

    enum class Utbetalingtype { UTBETALING, ETTERUTBETALING, ANNULLERING, REVURDERING }

    companion object {
        private val IkkeUtbetalingsdagtyper = listOf("AvvistDag", "Fridag", "Feriedag", "Permisjonsdag", "Arbeidsdag")

        fun fromJson(packet: JsonMessage) = fromJson(objectMapper.readTree(packet.toJson()))

        fun fromJson(packet: JsonNode): Utbetaling {
            return Utbetaling(
                utbetalingId = packet["utbetalingId"].let { UUID.fromString(it.asText()) },
                fødselsnummer = packet["fødselsnummer"].asText(),
                aktørId = packet["aktørId"].asText(),
                organisasjonsnummer = packet["organisasjonsnummer"].asText(),
                fom = packet["fom"].asLocalDate(),
                tom = packet["tom"].asLocalDate(),
                forbrukteSykedager = packet["forbrukteSykedager"].asInt(),
                gjenståendeSykedager = packet["gjenståendeSykedager"].asInt(),
                maksdato = packet["maksdato"].asLocalDate(),
                automatiskBehandling = packet["automatiskBehandling"].asBoolean(),
                arbeidsgiverOppdrag = packet["arbeidsgiverOppdrag"].tilOppdragDto(),
                personOppdrag = packet["personOppdrag"].tilOppdragDto(),
                type = Utbetalingtype.valueOf(packet["type"].asText()),
                ident = packet["ident"].asText(),
                epost = packet["epost"].asText(),
                opprettet = packet["@opprettet"].asLocalDateTime(),
                utbetalingsdager = packet.utbetalingsdager
            )
        }

        private fun JsonNode.tilOppdragDto(): OppdragDto {
            return OppdragDto(
                mottaker = this["mottaker"].asText(),
                fagområde = this["fagområde"].asText(),
                fagsystemId = this["fagsystemId"].asText(),
                nettoBeløp = this["nettoBeløp"].asInt(),
                utbetalingslinjer = this["linjer"].map { linje ->
                    OppdragDto.UtbetalingslinjeDto(
                        fom = linje["fom"].asLocalDate(),
                        tom = linje["tom"].asLocalDate(),
                        dagsats = (linje.path("sats").takeUnless { it.isMissingOrNull() } ?: linje.path("dagsats")).asInt(),
                        totalbeløp = linje["totalbeløp"].asInt(),
                        grad = linje["grad"].asDouble(),
                        stønadsdager = linje["stønadsdager"].asInt(),
                        statuskode = linje.findValue("statuskode")?.asText()
                    )
                }
            )
        }

        private val JsonNode.utbetalingsdager get() = path("utbetalingsdager").map { dag ->
            UtbetalingdagDto(
                dato = dag["dato"].asLocalDate(),
                type = dag["type"].asText(),
                begrunnelser = dag.path("begrunnelser").takeUnless(JsonNode::isMissingOrNull)
                    ?.let { it.map { begrunnelse -> begrunnelse.asText() } } ?: emptyList()
            )
        }
    }

    data class OppdragDto(
        val mottaker: String,
        val fagområde: String,
        val fagsystemId: String,
        val nettoBeløp: Int,
        val utbetalingslinjer: List<UtbetalingslinjeDto>
    ) {
        data class UtbetalingslinjeDto(
            val fom: LocalDate,
            val tom: LocalDate,
            val dagsats: Int,
            val totalbeløp: Int,
            val grad: Double,
            val stønadsdager: Int,
            val statuskode: String?
        )

        internal fun linjer(mottakerType: VedtakPdfPayloadV2.MottakerType, navn: String): List<VedtakPdfPayloadV2.Linje> {
            return utbetalingslinjer.map { VedtakPdfPayloadV2.Linje(
                fom = it.fom,
                tom = it.tom,
                grad = it.grad.toInt(),
                dagsats = it.dagsats,
                mottaker = mottakerType.formater(navn),
                mottakerType = mottakerType,
                totalbeløp = it.totalbeløp,
                erOpphørt = it.statuskode == "OPPH"
            )}
        }
    }

    data class UtbetalingdagDto(
        val dato: LocalDate,
        val type: String,
        val begrunnelser: List<String>
    )

    private fun <R> Collection<R>.singleOrNullOrThrow() =
        if (size < 2) this.firstOrNull()
        else throw IllegalStateException("Listen inneholder mer enn ett element!")
}
