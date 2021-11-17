package no.nav.helse.spre.gosys.utbetaling

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.spre.gosys.vedtak.VedtakMediator
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetData
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

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
    val type: Utbetalingtype,
    val ident: String,
    val epost: String,
    val vedtaksperiodeIder: List<UUID>,
    val ikkeUtbetalingsdager: List<UtbetalingdagDto>,
    val opprettet: LocalDateTime
) {

    private fun alleVedtakOrNull(vedtakFattetDao: VedtakFattetDao) = vedtakFattetDao.finnVedtakFattetData(utbetalingId).let { vedtakFattetHendelser ->
        // Hvis vi har alle vedtak knyttet til utbetalingen kan vi gå videre med opprettelse av dokument
        // Hvis vi ikke har noen vedtak enda så null:
        if(vedtakFattetHendelser.isEmpty()) return null
        if (vedtaksperiodeIder.harAlle(vedtakFattetHendelser)) vedtakFattetHendelser
        else null
    }

    private fun List<UUID>.harAlle(vedtakFattet: List<VedtakFattetData>) = all { vedtaksperiodeId ->
        vedtaksperiodeId in vedtakFattet.map { it.vedtaksperiodeId }
    }

    internal fun avgjørVidereBehandling(vedtakFattetDao: VedtakFattetDao, vedtakMediator: VedtakMediator) {
        alleVedtakOrNull(vedtakFattetDao)?.let { vedtakFattetHendelser ->
            val fom = vedtakFattetHendelser.minOf { it.fom }
            val tom = vedtakFattetHendelser.maxOf { it.tom }
            val vedtaksperiode = vedtakFattetHendelser.first()
            check(vedtakFattetHendelser.all { it.fødselsnummer == fødselsnummer }) {
                "Alvorlig feil: Vedtaket peker på utbetaling med et annet fødselnummer"
            }
            vedtakMediator.opprettSammenslåttVedtak(
                fom,
                tom,
                vedtaksperiode.sykepengegrunnlag,
                vedtaksperiode.grunnlagForSykepengegrunnlag,
                vedtaksperiode.skjæringstidspunkt,
                this
            )
        }
    }

    enum class Utbetalingtype { UTBETALING, ETTERUTBETALING, ANNULLERING, REVURDERING }

    companion object {
        fun fromJson(packet: JsonMessage): Utbetaling {
            val arbeidsgiverOppdrag = packet["arbeidsgiverOppdrag"].let { oppdrag ->
                OppdragDto(
                    mottaker = oppdrag["mottaker"].asText(),
                    fagområde = oppdrag["fagområde"].asText(),
                    fagsystemId = oppdrag["fagsystemId"].asText(),
                    nettoBeløp = oppdrag["nettoBeløp"].asInt(),
                    utbetalingslinjer = oppdrag["linjer"].map { linje ->
                        OppdragDto.UtbetalingslinjeDto(
                            fom = linje["fom"].asLocalDate(),
                            tom = linje["tom"].asLocalDate(),
                            dagsats = linje["dagsats"].asInt(),
                            totalbeløp = linje["totalbeløp"].asInt(),
                            grad = linje["grad"].asDouble(),
                            stønadsdager = linje["stønadsdager"].asInt()
                        )
                    }
                )
            }
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
                arbeidsgiverOppdrag = arbeidsgiverOppdrag,
                type = Utbetalingtype.valueOf(packet["type"].asText()),
                ident = packet["ident"].asText(),
                epost = packet["epost"].asText(),
                vedtaksperiodeIder = packet["vedtaksperiodeIder"].toList()
                    .map { UUID.fromString(it.asText()) },
                opprettet = packet["@opprettet"].asLocalDateTime(),
                ikkeUtbetalingsdager = packet["utbetalingsdager"].toList()
                    .filter { dag -> erIkkeUtbetaltDag(dag) }
                    .map { dag ->
                        UtbetalingdagDto(
                            dato = dag["dato"].asLocalDate(),
                            type = dag["type"].asText(),
                            begrunnelser = dag.path("begrunnelser").takeUnless(JsonNode::isMissingOrNull)
                                ?.let { it.map { begrunnelse -> begrunnelse.asText() } } ?: emptyList())
                    }
            )
        }


        fun fromJson(packet: JsonNode): Utbetaling {
            val arbeidsgiverOppdrag = packet["arbeidsgiverOppdrag"].let { oppdrag ->
                OppdragDto(
                    mottaker = oppdrag["mottaker"].asText(),
                    fagområde = oppdrag["fagområde"].asText(),
                    fagsystemId = oppdrag["fagsystemId"].asText(),
                    nettoBeløp = oppdrag["nettoBeløp"].asInt(),
                    utbetalingslinjer = oppdrag["linjer"].map { linje ->
                        OppdragDto.UtbetalingslinjeDto(
                            fom = linje["fom"].asLocalDate(),
                            tom = linje["tom"].asLocalDate(),
                            dagsats = linje["dagsats"].asInt(),
                            totalbeløp = linje["totalbeløp"].asInt(),
                            grad = linje["grad"].asDouble(),
                            stønadsdager = linje["stønadsdager"].asInt()
                        )
                    }
                )
            }
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
                arbeidsgiverOppdrag = arbeidsgiverOppdrag,
                type = Utbetalingtype.valueOf(packet["type"].asText()),
                ident = packet["ident"].asText(),
                epost = packet["epost"].asText(),
                vedtaksperiodeIder = packet["vedtaksperiodeIder"].toList()
                    .map { UUID.fromString(it.asText()) },
                opprettet = packet["@opprettet"].asLocalDateTime(),
                ikkeUtbetalingsdager = packet["utbetalingsdager"].toList()
                    .filter(::erIkkeUtbetaltDag)
                    .map { dag ->
                        UtbetalingdagDto(
                            dato = dag["dato"].asLocalDate(),
                            type = dag["type"].asText(),
                            begrunnelser = dag.path("begrunnelser").takeUnless(JsonNode::isMissingOrNull)
                                ?.let { it.map { begrunnelse -> begrunnelse.asText() } } ?: emptyList()
                        )
                    }
            )
        }

        private fun erIkkeUtbetaltDag(dag: JsonNode) =
            listOf("AvvistDag", "Fridag", "Arbeidsdag").contains(dag["type"].asText())
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
            val stønadsdager: Int
        )
    }

    data class UtbetalingdagDto(
        val dato: LocalDate,
        val type: String,
        val begrunnelser: List<String>
    )
}
