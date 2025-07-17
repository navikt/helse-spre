package no.nav.helse.spre.gosys.utbetaling

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.helse.spre.gosys.objectMapper

data class Utbetaling(
    val utbetalingId: UUID,
    val fødselsnummer: String,
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
    val utbetalingsdager: List<UtbetalingdagDto>
) {
    internal fun søknadsperiode(vedtaksperiode: Pair<LocalDate, LocalDate>): Pair<LocalDate, LocalDate> {
        val dager = utbetalingsdager.filter { it.dato >= vedtaksperiode.first && it.dato <= vedtaksperiode.second }

        val fom = when (dager.none { it.type == "ArbeidIkkeGjenopptattDag" }) {
            true -> vedtaksperiode.first
            false -> dager.firstOrNull { it.type != "ArbeidIkkeGjenopptattDag" }?.dato ?: vedtaksperiode.first
        }

        return fom to vedtaksperiode.second
    }

    fun erRevurdering(): Boolean {
        return this.type == Utbetalingtype.REVURDERING
    }

    enum class Utbetalingtype { UTBETALING, ETTERUTBETALING, ANNULLERING, REVURDERING }

    companion object {
        val IkkeUtbetalingsdagtyper = listOf("AvvistDag", "Fridag", "Feriedag", "Permisjonsdag", "Arbeidsdag", "AndreYtelser")

        fun fromJson(packet: JsonMessage) = fromJson(objectMapper.readTree(packet.toJson()))

        fun fromJson(packet: JsonNode): Utbetaling {
            return Utbetaling(
                utbetalingId = packet["utbetalingId"].let { UUID.fromString(it.asText()) },
                fødselsnummer = packet["fødselsnummer"].asText(),
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
                        grad = linje["grad"].asInt(),
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
            val grad: Int,
            val stønadsdager: Int,
            val statuskode: String?
        ) {
            val erOpphørt = statuskode == "OPPH"
        }
    }

    data class UtbetalingdagDto(
        val dato: LocalDate,
        val type: String,
        val begrunnelser: List<String>
    )
}
