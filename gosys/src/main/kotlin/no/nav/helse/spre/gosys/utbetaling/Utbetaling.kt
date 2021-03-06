package no.nav.helse.spre.gosys.utbetaling

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.isMissingOrNull
import java.time.LocalDate
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
    val ikkeUtbetalingsdager: List<UtbetalingdagDto>
) {

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
