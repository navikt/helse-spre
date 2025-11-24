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
import no.nav.helse.spre.gosys.journalfør
import no.nav.helse.spre.gosys.logg
import no.nav.helse.spre.gosys.sikkerLogg
import no.nav.helse.spre.gosys.vedtakFattet.MeldingOmVedtakRepository
import no.nav.helse.spre.gosys.vedtakFattet.pdf.PdfJournalfører
import no.nav.helse.spre.gosys.vedtakFattet.pdf.PdfProduserer

internal class UtbetalingUtbetaltMedEllerUtenUtbetalingRiver(
    rapidsConnection: RapidsConnection,
    private val utbetalingDao: UtbetalingDao,
    private val duplikatsjekkDao: DuplikatsjekkDao,
    private val pdfProduserer: PdfProduserer,
    private val pdfJournalfører: PdfJournalfører,
    private val meldingOmVedtakRepository: MeldingOmVedtakRepository
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition { it.requireAny("@event_name", listOf("utbetaling_utbetalt", "utbetaling_uten_utbetaling")) }
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
        logg.error("forstod ikke utbetaling-melding. (se sikkerlogg for melding)")
        sikkerLogg.error("forstod ikke utbetaling-melding:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val eventName = packet["@event_name"].asText()
        val id = UUID.fromString(packet["@id"].asText())
        if (duplikatsjekkDao.erDuplikat(id)) {
            logg.info("Hopper over ${eventName}-melding $id som allerede er behandlet (duplikatsjekk)")
            return
        }
        val utbetaling = Utbetaling.fromJson(packet)

        if (utbetalingDao.finnUtbetalingData(utbetaling.utbetalingId) != null)
            return logg.info("Har allerede lagret en utbetaling med utbetalingId=${utbetaling.utbetalingId}")

        utbetalingDao.lagre(id, eventName, utbetaling, packet.toJson())

        val vedtakFattetRad = meldingOmVedtakRepository.finn(utbetaling.utbetalingId)
            ?: return logg.info("Utbetaling lagret, venter på melding om vedtak")

        if (vedtakFattetRad.erJournalført())
            error("Fant et journalført vedtak for en utbetaling vi ikke har lagret")

        journalfør(id, utbetaling, vedtakFattetRad, pdfProduserer, pdfJournalfører, duplikatsjekkDao)
    }
}
