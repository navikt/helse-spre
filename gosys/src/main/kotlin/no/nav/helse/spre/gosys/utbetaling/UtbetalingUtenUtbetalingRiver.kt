package no.nav.helse.spre.gosys.utbetaling

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.util.*
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.logg
import no.nav.helse.spre.gosys.sikkerLogg

internal class UtbetalingUtenUtbetalingRiver(
    rapidsConnection: RapidsConnection,
    private val utbetalingDao: UtbetalingDao,
    private val duplikatsjekkDao: DuplikatsjekkDao
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "utbetaling_uten_utbetaling") }
            validate {
                it.requireKey(
                    "fødselsnummer",
                    "@id",
                    "organisasjonsnummer",
                    "forbrukteSykedager",
                    "gjenståendeSykedager",
                    "automatiskBehandling",
                    "arbeidsgiverOppdrag",
                    "personOppdrag",
                    "utbetalingsdager",
                    "type",
                    "ident",
                    "epost"
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("maksdato", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.require("utbetalingId") { id -> UUID.fromString(id.asText()) }

                it.requireKey("arbeidsgiverOppdrag.mottaker", "arbeidsgiverOppdrag.fagområde", "arbeidsgiverOppdrag.fagsystemId",
                    "arbeidsgiverOppdrag.nettoBeløp")
                it.requireArray("arbeidsgiverOppdrag.linjer") {
                    require("fom", JsonNode::asLocalDate)
                    require("tom", JsonNode::asLocalDate)
                    requireKey("sats", "totalbeløp", "grad", "stønadsdager")
                    interestedIn("statuskode")
                }
                it.requireKey("personOppdrag.mottaker", "personOppdrag.fagområde", "personOppdrag.fagsystemId",
                    "personOppdrag.nettoBeløp")
                it.requireArray("personOppdrag.linjer") {
                    require("fom", JsonNode::asLocalDate)
                    require("tom", JsonNode::asLocalDate)
                    requireKey("sats", "totalbeløp", "grad", "stønadsdager")
                    interestedIn("statuskode")
                }
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        logg.error("forstod ikke utbetaling_utbetalt. (se sikkerlogg for melding)")
        sikkerLogg.error("forstod ikke utbetaling_utbetalt:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val id = UUID.fromString(packet["@id"].asText())
        try {
            duplikatsjekkDao.sjekkDuplikat(id) {
                lagreUtbetaling(id, packet, utbetalingDao)
            }
        } catch (err: Exception) {
            logg.error("Feil i melding $id i utbetaling uten utbetaling-river: ${err.message}", err)
            sikkerLogg.error("Feil i melding $id i utbetaling uten utbetaling-river: ${err.message}", err)
            throw err
        }
    }
}

