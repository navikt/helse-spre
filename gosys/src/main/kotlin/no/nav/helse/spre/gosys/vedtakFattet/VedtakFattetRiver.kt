package no.nav.helse.spre.gosys.vedtakFattet

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.logg
import no.nav.helse.spre.gosys.sikkerLogg
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.vedtak.VedtakMediator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val tjenestekall: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val vedtakFattetDao: VedtakFattetDao,
    private val utbetalingDao: UtbetalingDao,
    private val duplikatsjekkDao: DuplikatsjekkDao,
    private val vedtakMediator: VedtakMediator
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "vedtak_fattet")
                it.requireKey("sykepengegrunnlagsfakta")
            }
            validate { message ->
                message.requireKey(
                    "fødselsnummer",
                    "@id",
                    "vedtaksperiodeId",
                    "organisasjonsnummer",
                    "hendelser",
                    "sykepengegrunnlag",
                    "inntekt",
                    "grunnlagForSykepengegrunnlagPerArbeidsgiver",
                )
                message.require("fom", JsonNode::asLocalDate)
                message.require("tom", JsonNode::asLocalDate)
                message.require("skjæringstidspunkt", JsonNode::asLocalDate)
                message.require("@opprettet", JsonNode::asLocalDateTime)
                message.interestedIn("utbetalingId") { id -> UUID.fromString(id.asText()) }
                message.interestedIn("begrunnelser")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val id = UUID.fromString(packet["@id"].asText())
        try {
            duplikatsjekkDao.sjekkDuplikat(id) {
                val utbetalingId = packet["utbetalingId"].takeUnless(JsonNode::isMissingOrNull)?.let { UUID.fromString(it.asText()) }
                val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
                logg.info("vedtak_fattet leses inn for vedtaksperiode med vedtaksperiodeId $vedtaksperiodeId")

                val vedtakFattet = VedtakFattetData.fromJson(id, packet)
                val tidligereVedtakFattet = utbetalingId?.let { vedtakFattetDao.finnVedtakFattetData(it) }

                if (tidligereVedtakFattet != null) {
                    check(tidligereVedtakFattet.vedtaksperiodeId == vedtaksperiodeId) {
                        "det finnes et tidligere vedtak (vedtaksperiode ${tidligereVedtakFattet.vedtaksperiodeId}) med samme utbetalingId, som er ulik vedtaksperiode $vedtaksperiodeId"
                    }

                    if (vedtakFattetDao.erJournalført(tidligereVedtakFattet)) {
                        logg.warn("har allerede behandlet vedtak_fattet for vedtaksperiode $vedtaksperiodeId og utbetaling $utbetalingId")
                        return@sjekkDuplikat
                    }
                }

                vedtakFattetDao.lagre(vedtakFattet, packet.toJson())
                logg.info("vedtak_fattet lagret for vedtaksperiode med vedtaksperiodeId $vedtaksperiodeId på id $id")

                if (utbetalingId != null) {
                    val utbetaling = checkNotNull(utbetalingDao.finnUtbetalingData(utbetalingId)) {
                        "forventer å finne utbetaling for vedtak for $vedtaksperiodeId"
                    }

                    vedtakMediator.opprettSammenslåttVedtak(
                        vedtakFattet.fom,
                        vedtakFattet.tom,
                        vedtakFattet.sykepengegrunnlag,
                        vedtakFattet.grunnlagForSykepengegrunnlag,
                        vedtakFattet.skjæringstidspunkt,
                        vedtakFattet.sykepengegrunnlagsfakta,
                        vedtakFattet.begrunnelser,
                        utbetaling
                    ) {
                        vedtakFattetDao.journalført(vedtakFattet.id)
                    }
                }
            }
        }  catch (err: Exception) {
            logg.error("Feil i melding $id i vedtak fattet-river: ${err.message}", err)
            sikkerLogg.error("Feil i melding $id i vedtak fattet-river: ${err.message}", err)
            throw err
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        tjenestekall.info("Noe gikk galt: {}", problems.toExtendedReport())
    }
}
