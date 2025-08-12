package no.nav.helse.spre.gosys.vedtakFattet

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.speed.SpeedClient
import io.micrometer.core.instrument.MeterRegistry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.UUID.randomUUID
import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.EregClient
import no.nav.helse.spre.gosys.JoarkClient
import no.nav.helse.spre.gosys.JournalpostPayload
import no.nav.helse.spre.gosys.PdfClient
import no.nav.helse.spre.gosys.erUtvikling
import no.nav.helse.spre.gosys.finnOrganisasjonsnavn
import no.nav.helse.spre.gosys.hentNavn
import no.nav.helse.spre.gosys.logg
import no.nav.helse.spre.gosys.objectMapper
import no.nav.helse.spre.gosys.sikkerLogg
import no.nav.helse.spre.gosys.utbetaling.Utbetaling
import no.nav.helse.spre.gosys.utbetaling.Utbetaling.Companion.IkkeUtbetalingsdagtyper
import no.nav.helse.spre.gosys.utbetaling.Utbetaling.OppdragDto.UtbetalingslinjeDto
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.vedtak.AvvistPeriode
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload.IkkeUtbetalteDager
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload.Oppdrag
import no.nav.helse.spre.gosys.vedtak.slåSammen
import no.nav.helse.spre.gosys.vedtak.slåSammenLikePerioder
import no.nav.helse.spre.gosys.vedtakFattet.pdf.PdfSomething.lagPdfPayload

internal class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val vedtakFattetDao: VedtakFattetDao,
    private val utbetalingDao: UtbetalingDao,
    private val duplikatsjekkDao: DuplikatsjekkDao,
    private val pdfClient: PdfClient,
    private val joarkClient: JoarkClient,
    private val eregClient: EregClient,
    private val speedClient: SpeedClient
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "vedtak_fattet")
                it.requireKey("sykepengegrunnlagsfakta")
                it.require("utbetalingId") { id -> UUID.fromString(id.asText()) }
                it.forbidValue("yrkesaktivitetstype", "SELVSTENDIG")
            }
            validate { message ->
                message.requireKey(
                    "@id",
                    "fødselsnummer",
                    "vedtaksperiodeId",
                    "sykepengegrunnlag"
                )
                message.require("fom", JsonNode::asLocalDate)
                message.require("tom", JsonNode::asLocalDate)
                message.require("skjæringstidspunkt", JsonNode::asLocalDate)
                message.require("@opprettet", JsonNode::asLocalDateTime)
                message.require("vedtakFattetTidspunkt", JsonNode::asLocalDateTime)
                message.interestedIn(
                    "begrunnelser",
                    "organisasjonsnummer",
                )
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        logg.error("forstod ikke vedtak_fattet. (se sikkerlogg for melding)")
        sikkerLogg.error("forstod ikke vedtak_fattet:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val id = UUID.fromString(packet["@id"].asText())
        try {
            duplikatsjekkDao.sjekkDuplikat(id) {
                val utbetalingId = packet["utbetalingId"].asText().toUUID()
                val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
                withMDC(
                    mapOf(
                        "meldingsreferanseId" to "$id",
                        "vedtaksperiodeId" to "$vedtaksperiodeId",
                        "utbetalingId" to "$utbetalingId"
                    )
                ) {
                    behandleMelding(
                        packet = packet,
                        meldingId = id,
                        utbetalingId = utbetalingId,
                        vedtaksperiodeId = vedtaksperiodeId
                    )
                }
            }
        } catch (err: Exception) {
            logg.error("Feil i melding $id i vedtak fattet-river: ${err.message}", err)
            sikkerLogg.error("Feil i melding $id i vedtak fattet-river: ${err.message}", err)
            throw err
        }
    }

    private fun behandleMelding(
        packet: JsonMessage,
        meldingId: UUID,
        utbetalingId: UUID,
        vedtaksperiodeId: UUID
    ) {
        logg.info("vedtak_fattet leses inn for vedtaksperiode med vedtaksperiodeId $vedtaksperiodeId")

        if (erDuplikatBehandling(utbetalingId, vedtaksperiodeId))
            return logg.warn("har allerede behandlet vedtak_fattet for vedtaksperiode $vedtaksperiodeId og utbetaling $utbetalingId")

        lagreVedtakFattet(packet = packet, meldingId = meldingId, utbetalingId = utbetalingId, vedtaksperiodeId = vedtaksperiodeId)

        val utbetaling = checkNotNull(utbetalingDao.finnUtbetalingData(utbetalingId)) {
            "forventer å finne utbetaling for vedtak for $vedtaksperiodeId"
        }
        check(utbetaling.type in setOf(Utbetaling.Utbetalingtype.UTBETALING, Utbetaling.Utbetalingtype.REVURDERING)) {
            "vedtaket for $vedtaksperiodeId peker på utbetalingtype ${utbetaling.type}. Forventer kun Utbetaling/Revurdering"
        }
        // justerer perioden ved å hoppe over AIG-dager i snuten (todo: gjøre dette i spleis?)
        val (søknadsperiodeFom, søknadsperiodeTom) = utbetaling.søknadsperiode(packet["fom"].asLocalDate() to packet["tom"].asLocalDate())

        val pdf = lagPdf(
            packet = packet,
            utbetaling = utbetaling,
            søknadsperiodeFom = søknadsperiodeFom,
            søknadsperiodeTom = søknadsperiodeTom
        )
        logg.debug("Hentet pdf")
        journalførPdf(
            pdf = pdf,
            vedtakFattetMeldingId = meldingId,
            utbetaling = utbetaling,
            søknadsperiodeFom = søknadsperiodeFom,
            søknadsperiodeTom = søknadsperiodeTom
        )
    }

    private fun lagPdf(
        packet: JsonMessage,
        utbetaling: Utbetaling,
        søknadsperiodeFom: LocalDate,
        søknadsperiodeTom: LocalDate
    ): String {
        val organisasjonsnavn = runBlocking { finnOrganisasjonsnavn(eregClient, utbetaling.organisasjonsnummer) }
        logg.debug("Hentet organisasjonsnavn")

        val navn = hentNavn(speedClient, packet["fødselsnummer"].asText(), randomUUID().toString()) ?: ""
        logg.debug("Hentet søkernavn")

        val vedtakPdfPayload = lagPdfPayload(
            packet = packet,
            utbetaling = utbetaling,
            søknadsperiodeFom = søknadsperiodeFom,
            søknadsperiodeTom = søknadsperiodeTom,
            navn = navn,
            organisasjonsnavn = organisasjonsnavn
        )
        if (erUtvikling) sikkerLogg.info("vedtak-payload: ${objectMapper.writeValueAsString(vedtakPdfPayload)}")
        return runBlocking { pdfClient.hentVedtakPdf(vedtakPdfPayload) }
    }

    private fun lagreVedtakFattet(
        packet: JsonMessage,
        meldingId: UUID,
        utbetalingId: UUID,
        vedtaksperiodeId: UUID
    ) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        vedtakFattetDao.lagre(meldingId, utbetalingId, fødselsnummer, packet.toJson())
        logg.info("vedtak_fattet lagret for vedtaksperiode med vedtaksperiodeId $vedtaksperiodeId på id $meldingId")
    }

    private fun erDuplikatBehandling(utbetalingId: UUID, vedtaksperiodeId: UUID): Boolean {
        val tidligereVedtakFattet = vedtakFattetDao.finnVedtakFattetData(utbetalingId) ?: return false

        check(tidligereVedtakFattet.vedtaksperiodeId == vedtaksperiodeId) {
            "det finnes et tidligere vedtak (vedtaksperiode ${tidligereVedtakFattet.vedtaksperiodeId}) " +
                "med samme utbetalingId, som er ulik vedtaksperiode $vedtaksperiodeId"
        }

        return vedtakFattetDao.erJournalført(tidligereVedtakFattet.id)
    }

    private fun journalførPdf(
        pdf: String,
        vedtakFattetMeldingId: UUID,
        utbetaling: Utbetaling,
        søknadsperiodeFom: LocalDate,
        søknadsperiodeTom: LocalDate
    ) {
        val journalpostPayload = JournalpostPayload(
            tittel = journalpostTittel(utbetaling.type),
            bruker = JournalpostPayload.Bruker(id = utbetaling.fødselsnummer),
            dokumenter = listOf(
                JournalpostPayload.Dokument(
                    tittel = dokumentTittel(type = utbetaling.type, fom = søknadsperiodeFom, tom = søknadsperiodeTom),
                    dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                )
            ),
            eksternReferanseId = utbetaling.utbetalingId.toString(),
        )

        if (!journalførPdf(utbetaling.utbetalingId, journalpostPayload)) return logg.warn("Feil oppstod under journalføring av vedtak")

        vedtakFattetDao.journalfør(vedtakFattetMeldingId)

        logg.info("Vedtak journalført for utbetalingId: ${utbetaling.utbetalingId}")
        sikkerLogg.info("Vedtak journalført for fødselsnummer=${utbetaling.fødselsnummer} utbetalingId: ${utbetaling.utbetalingId}")
    }

    private fun journalførPdf(
        utbetalingId: UUID,
        journalpostPayload: JournalpostPayload
    ): Boolean = runBlocking { joarkClient.opprettJournalpost(utbetalingId, journalpostPayload) }

    private fun dokumentTittel(
        type: Utbetaling.Utbetalingtype,
        fom: LocalDate,
        tom: LocalDate
    ): String = when (type) {
        Utbetaling.Utbetalingtype.UTBETALING -> "Sykepenger behandlet, ${fom.somNorskDato()} - ${tom.somNorskDato()}"
        Utbetaling.Utbetalingtype.ETTERUTBETALING -> "Sykepenger etterutbetalt, ${fom.somNorskDato()} - ${tom.somNorskDato()}"
        Utbetaling.Utbetalingtype.REVURDERING -> "Sykepenger revurdert, ${fom.somNorskDato()} - ${tom.somNorskDato()}"
        Utbetaling.Utbetalingtype.ANNULLERING -> throw IllegalArgumentException("Forsøkte å opprette vedtaksnotat for annullering")
    }

    private fun LocalDate.somNorskDato(): String = format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

    private fun journalpostTittel(type: Utbetaling.Utbetalingtype): String {
        return when (type) {
            Utbetaling.Utbetalingtype.UTBETALING -> "Vedtak om sykepenger"
            Utbetaling.Utbetalingtype.ETTERUTBETALING -> "Vedtak om etterutbetaling av sykepenger"
            Utbetaling.Utbetalingtype.REVURDERING -> "Vedtak om revurdering av sykepenger"
            Utbetaling.Utbetalingtype.ANNULLERING -> throw IllegalArgumentException("Forsøkte å opprette vedtaksnotat for annullering")
        }
    }
}
